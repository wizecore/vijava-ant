package com.wizecore.ant.vijava;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;

import org.apache.tools.ant.BuildException;

import com.vmware.vim25.CustomizationSpec;
import com.vmware.vim25.CustomizationSpecItem;
import com.vmware.vim25.Description;
import com.vmware.vim25.HostSystemConnectionState;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.VirtualCdrom;
import com.vmware.vim25.VirtualCdromIsoBackingInfo;
import com.vmware.vim25.VirtualDevice;
import com.vmware.vim25.VirtualDeviceConfigSpec;
import com.vmware.vim25.VirtualDeviceConfigSpecFileOperation;
import com.vmware.vim25.VirtualDeviceConfigSpecOperation;
import com.vmware.vim25.VirtualDeviceConnectInfo;
import com.vmware.vim25.VirtualDisk;
import com.vmware.vim25.VirtualDiskFlatVer2BackingInfo;
import com.vmware.vim25.VirtualEthernetCard;
import com.vmware.vim25.VirtualEthernetCardNetworkBackingInfo;
import com.vmware.vim25.VirtualFloppy;
import com.vmware.vim25.VirtualFloppyImageBackingInfo;
import com.vmware.vim25.VirtualIDEController;
import com.vmware.vim25.VirtualLsiLogicController;
import com.vmware.vim25.VirtualMachineBootOptions;
import com.vmware.vim25.VirtualMachineBootOptionsBootableCdromDevice;
import com.vmware.vim25.VirtualMachineBootOptionsBootableDevice;
import com.vmware.vim25.VirtualMachineBootOptionsBootableDiskDevice;
import com.vmware.vim25.VirtualMachineBootOptionsBootableEthernetDevice;
import com.vmware.vim25.VirtualMachineBootOptionsBootableFloppyDevice;
import com.vmware.vim25.VirtualMachineCloneSpec;
import com.vmware.vim25.VirtualMachineConfigOption;
import com.vmware.vim25.VirtualMachineConfigSpec;
import com.vmware.vim25.VirtualMachineFileInfo;
import com.vmware.vim25.VirtualMachineRelocateSpec;
import com.vmware.vim25.VirtualMachineRuntimeInfo;
import com.vmware.vim25.VirtualPCNet32;
import com.vmware.vim25.VirtualSCSISharing;
import com.vmware.vim25.mo.ComputeResource;
import com.vmware.vim25.mo.Datacenter;
import com.vmware.vim25.mo.Datastore;
import com.vmware.vim25.mo.EnvironmentBrowser;
import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.ResourcePool;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.VirtualMachine;


/**
 * Creates virtual machine. 
 * Virtual machine can be created from template or from scratch.
 * 
 * This can be a part of &lt;vm&gt; set of task (to perform a series of vm operations)
 * or can be run on it`s own.
 * 
 * @ant.task name="vmCreate" category="vmCreate"
 * @author huksley
 */
public class VMCreateTask extends AbstractVMTask {
	
	final static int INDEX_SCSICONTROLLER = 1;
	final static int INDEX_DISK = 2;
	final static int INDEX_NET = 3;
	final static int INDEX_ISO = 4;
	final static int INDEX_FLOPPY = 5;
	
	String resourcePool;
	String template;
	String datastore;	
	String datastoreType = "sanfs"; // netfs
	String vm;	
	boolean wait = false;	
	boolean powerOn = false; 
	int cpu = 1;
	long memory = 1024; // in MB
	// http://vijava.sourceforge.net/vSphereAPIDoc/ver5/ReferenceGuide/vim.vm.GuestOsDescriptor.GuestOsIdentifier.html
	String guestType = "winNetEnterpriseGuest"; // sles10Guest
	long diskSize = 10 * 1024; // in MB
	String diskMode = "persistent";
	int deviceKey = 1000;
	String netName = "VM Network";
	String nicName = "NetAdapter 1";
	String annotation = "VirtualMachine Annotation";
	String iso = null;
	String bootOrder = null ; // "cdrom,disk,net,floppy";
	String floppyImage = null;
	String customization = null;
	
	@Override
	public void execute() throws BuildException {
		super.execute();
		
		try {
			ServiceInstance si = getServiceInstance();
			try {
				Folder rootFolder = si.getRootFolder();
				
				if (vm == null) {
					throw new BuildException("Target virtual machine name not set!");
				}
				
				VirtualMachine vmTemplate = null;				
				if (template != null && !template.equals("")) {
					vmTemplate = (VirtualMachine) new InventoryNavigator(rootFolder).searchManagedEntity("VirtualMachine", template);
					if (vmTemplate == null) {
						throw new BuildException("Unknown virtual machine to use as template: " + template);
					}
				}
					
				HostSystem targetHost = null;
				if (host == null) {
					ManagedEntity[] hosts = (ManagedEntity[]) new InventoryNavigator(rootFolder).searchManagedEntities("HostSystem");
					if (hosts == null || hosts.length == 0) {
						throw new BuildException("No hosts found!");
					}
					
					for (int i = 0; i < hosts.length; i++) {
						HostSystem h = (HostSystem) hosts[i];
						if (h.getRuntime().getConnectionState() == HostSystemConnectionState.connected) {
							targetHost = h;
							log.info("Selected first available host: " + targetHost.getName());
							break;
						}
					}
				} else {
					targetHost = (HostSystem) new InventoryNavigator(rootFolder).searchManagedEntity("HostSystem", host);
				}
				
				if (targetHost == null) {
					throw new BuildException("Unable to find target host - " + (host != null ? host : "<first available>"));
				}
				
				ResourcePool targetPool = null;
				if (resourcePool == null) {
					ComputeResource hostResource = (ComputeResource) targetHost.getParent();
					ManagedEntity[] rp = (ManagedEntity[]) new InventoryNavigator(rootFolder).searchManagedEntities("ResourcePool");
					for (int i = 0; i < rp.length; i++) {
						ResourcePool r = (ResourcePool) rp[i];
						if (same(r.getOwner(), hostResource)) {
							targetPool = r;
						}
					}
				} else {
					targetPool = (ResourcePool) new InventoryNavigator(rootFolder).searchManagedEntity("ResourcePool", resourcePool);
				}
				
				if (targetPool == null && resourcePool != null) {
					throw new BuildException("Can`t find resource pool " + resourcePool);
				} else
				if (targetPool == null) {
					throw new BuildException("Can`t find at least one resource pool on host " + targetHost.getName());
				}
				
				Datastore targetDatastore = null;
				Datastore[] ds = targetHost.getDatastores();
				for (int i = 0; i < ds.length; i++) {
					if (datastore == null && ds[i].getInfo().getUrl().startsWith(datastoreType + "://")) {
						targetDatastore = ds[i];
						break;
					} else
					if (datastore != null && ds[i].getName().equals(datastore)) {
						targetDatastore = ds[i];
						break;
					}
				}
				
				if (targetDatastore == null && datastore != null) {
					throw new BuildException("Can`t find datastore " + datastore + " on host " + targetHost.getName());
				} else
				if (targetDatastore == null) {
					throw new BuildException("Can`t find " + datastoreType + " datastore on host " + targetHost.getName());
				}
				
				VirtualMachineBootOptions boot = null;
				if (bootOrder != null && !bootOrder.equals("")) {
					boot = new VirtualMachineBootOptions();
					String[] bb = bootOrder.split("[\\t ]*[,\\n]+[\\t ]*");
					VirtualMachineBootOptionsBootableDevice[] bo = new VirtualMachineBootOptionsBootableDevice[bb.length];
					for (int i = 0; i < bb.length; i++) {
						bb[i] = bb[i].trim();
						if (bb[i].equals("disk")) {
							log.info("Booting from first disk");
							VirtualMachineBootOptionsBootableDiskDevice d = new VirtualMachineBootOptionsBootableDiskDevice();
							d.setDeviceKey(deviceKey + INDEX_DISK);
							bo[i] = d;
						} else
						if (bb[i].equals("net")) {
							log.info("Booting from net");
							VirtualMachineBootOptionsBootableEthernetDevice d = new VirtualMachineBootOptionsBootableEthernetDevice();
							d.setDeviceKey(deviceKey + INDEX_NET);
							bo[i] = d;
						} else
						if (bb[i].equals("cdrom")) {
							log.info("Booting from cdrom");
							VirtualMachineBootOptionsBootableCdromDevice d = new VirtualMachineBootOptionsBootableCdromDevice();
							bo[i] = d;
						} else
						if (bb[i].equals("floppy")) {
							log.info("Booting from floppy");
							VirtualMachineBootOptionsBootableFloppyDevice d = new VirtualMachineBootOptionsBootableFloppyDevice();
							bo[i] = d;
						} else {
							throw new BuildException("Unknown boot device for order: " + bb[i]);
						}
					}
					boot.setBootOrder(bo);
				}
			
				
				if (vmTemplate != null) {
					log.info("Creating VM from template " + vmTemplate.getName() + " on host " + 
						targetHost.getName() + " in datastore " + targetDatastore.getName() + ", resource pool " + targetPool.getName());
					
					VirtualMachineCloneSpec cloneSpec = new VirtualMachineCloneSpec();
					VirtualMachineRelocateSpec spec = new VirtualMachineRelocateSpec();
					spec.setPool(targetPool.getMOR());
					spec.setHost(targetHost.getMOR());
					spec.setDatastore(targetDatastore.getMOR());
					cloneSpec.setLocation(spec);
					cloneSpec.setPowerOn(powerOn);
					cloneSpec.setTemplate(false);
					
					if (customization != null) {
						CustomizationSpecItem cc = si.getCustomizationSpecManager().getCustomizationSpec(customization);
						if (cc == null) {
							throw new BuildException("Customization not found: " + customization);
						}
						log.info("Applying customization " + customization);
						CustomizationSpec cust = cc.getSpec();
						cloneSpec.setCustomization(cust);						
					}
					
					VirtualMachineConfigSpec config = new VirtualMachineConfigSpec();
					if (boot != null) {
						config.setBootOptions(boot);
					}
					cloneSpec.setConfig(config);
					runTask(vmTemplate.cloneVM_Task((Folder) vmTemplate.getParent(), vm, cloneSpec), wait);
				} else {
					if (!wait && powerOn) {
						throw new BuildException("For powerOn=true to have effect, wait must be set to true!");
					}
					
					ManagedEntity[] dc = (ManagedEntity[]) new InventoryNavigator(rootFolder).searchManagedEntities("Datacenter");
					Folder vmFolder = ((Datacenter) dc[0]).getVmFolder();
					log.info("Datacenter: " + dc[0].getName() + ", VM folder: " + vmFolder);					

					// create vm config spec
					VirtualMachineConfigSpec vmSpec = new VirtualMachineConfigSpec();
					vmSpec.setName(vm);
					vmSpec.setAnnotation(annotation);
					vmSpec.setMemoryMB(memory);
					vmSpec.setNumCPUs(cpu);
					vmSpec.setGuestId(guestType);
					
					List<VirtualDeviceConfigSpec> devices = new ArrayList<VirtualDeviceConfigSpec>();
					VirtualDeviceConfigSpec scsiSpec = createSCSIController(deviceKey);
					devices.add(scsiSpec);
					
					VirtualDeviceConfigSpec diskSpec = createDisk(targetDatastore.getName(), deviceKey, diskSize * 1024, diskMode);
					devices.add(diskSpec);
					
					VirtualDeviceConfigSpec nicSpec = createNIC(deviceKey, netName, nicName);
					devices.add(nicSpec);
					
					if (iso != null) {
						Datastore isods = iso.startsWith("[") ? null : targetDatastore;
						String filename = iso;
						if (isods == null) {
							String dsName = iso.substring(1, iso.indexOf("]")).trim();
							isods = (Datastore) new InventoryNavigator(rootFolder).searchManagedEntity("Datastore", dsName);
						} else {
							filename = "[" + targetDatastore.getName() + "] " + iso;
						}
						
						VirtualDeviceConfigSpec isoSpec = createISO(deviceKey, targetHost, isods, filename);
						devices.add(isoSpec);
					}
					
					if (floppyImage != null) {
						Datastore fds = floppyImage.startsWith("[") ? null : targetDatastore;
						String filename = floppyImage;
						if (fds == null) {
							String dsName = floppyImage.substring(1, floppyImage.indexOf("]")).trim();
							fds = (Datastore) new InventoryNavigator(rootFolder).searchManagedEntity("Datastore", dsName);
						} else {
							filename = "[" + targetDatastore.getName() + "] " + floppyImage;
						}
						
						VirtualDeviceConfigSpec floppySpec = createFloppy(deviceKey, targetHost, fds, filename);
						devices.add(floppySpec);
					}
					
					vmSpec.setDeviceChange((VirtualDeviceConfigSpec[]) devices.toArray(new VirtualDeviceConfigSpec[devices.size()]));
					
					if (boot != null) {
						vmSpec.setBootOptions(boot);
					}
					
					// create vm file info for the vmx file
					VirtualMachineFileInfo vmfi = new VirtualMachineFileInfo();
					vmfi.setVmPathName("[" + targetDatastore.getName() +"]");
					vmSpec.setFiles(vmfi);
					
					log.info("Creating VM " + vm + " on host " + 
						targetHost.getName() + " in datastore " + targetDatastore.getName() + ", resource pool " + targetPool.getName());
					runTask(vmFolder.createVM_Task(vmSpec, targetPool, targetHost), wait);
					
					if (wait && powerOn) {
						VirtualMachine target = (VirtualMachine) new InventoryNavigator(rootFolder).searchManagedEntity("VirtualMachine", vm);
						if (target == null) {
							throw new BuildException("Can`t find created VM: " + vm);
						}
						log.info("Powering on VM " + vm);
						runTask(target.powerOnVM_Task(null), wait);
					}
				}
			} finally {
				closeServerInstance(si);
			}
		} catch (InterruptedException e) {
			throw new BuildException(e);
		} catch (IOException e) {
			throw new BuildException(e);
		}
	}
	
	/**
	CONTROLLER IS IMPLICIT
	protected static VirtualDeviceConfigSpec createIDEController(int cKey) {
		VirtualDeviceConfigSpec ideSpec = new VirtualDeviceConfigSpec();
		ideSpec.setOperation(VirtualDeviceConfigSpecOperation.add);
		VirtualIDEController ide = new VirtualIDEController();
		ide.setKey(cKey + INDEX_IDECONTROLLER);
		ideSpec.setDevice(ide);
		return ideSpec;
	}
	*/

	protected static VirtualDeviceConfigSpec createSCSIController(int cKey) {
		VirtualDeviceConfigSpec scsiSpec = new VirtualDeviceConfigSpec();
		scsiSpec.setOperation(VirtualDeviceConfigSpecOperation.add);
		VirtualLsiLogicController scsiCtrl = new VirtualLsiLogicController();
		scsiCtrl.setKey(cKey + INDEX_SCSICONTROLLER);
		scsiCtrl.setBusNumber(0);
		scsiCtrl.setSharedBus(VirtualSCSISharing.noSharing);
		scsiSpec.setDevice(scsiCtrl);
		return scsiSpec;
	}
	
	protected static VirtualDeviceConfigSpec createISO(int cKey, HostSystem host, Datastore ds, String fileName) throws RemoteException {		
		VirtualDeviceConfigSpec cdSpec = new VirtualDeviceConfigSpec();
		cdSpec.setOperation(VirtualDeviceConfigSpecOperation.add);
		
		VirtualCdrom cdInfo = new VirtualCdrom();
		VirtualCdromIsoBackingInfo iso = new VirtualCdromIsoBackingInfo();
		VirtualDeviceConnectInfo conn = new VirtualDeviceConnectInfo();
		iso.setDatastore(ds.getMOR());
		iso.setFileName(fileName);	
		conn.setStartConnected(true);
		cdInfo.setBacking(iso);
		cdInfo.setConnectable(conn);
		VirtualDevice ideController = getIDEController(host);
		cdInfo.setControllerKey(ideController.key);
		cdInfo.setUnitNumber(0);
		cdInfo.setKey(- INDEX_ISO);
		cdSpec.setDevice(cdInfo);
		return cdSpec;
	}
	
	protected static VirtualDeviceConfigSpec createFloppy(int cKey, HostSystem host, Datastore ds, String fileName) throws RemoteException {		
		VirtualDeviceConfigSpec cdSpec = new VirtualDeviceConfigSpec();
		cdSpec.setOperation(VirtualDeviceConfigSpecOperation.add);
		
		VirtualFloppy floppyInfo = new VirtualFloppy();
		VirtualFloppyImageBackingInfo flp = new VirtualFloppyImageBackingInfo();
		VirtualDeviceConnectInfo conn = new VirtualDeviceConnectInfo();
		flp.setDatastore(ds.getMOR());
		flp.setFileName(fileName);	
		conn.setStartConnected(true);
		floppyInfo.setBacking(flp);
		floppyInfo.setConnectable(conn);
		floppyInfo.setKey(- INDEX_FLOPPY);
		cdSpec.setDevice(floppyInfo);
		return cdSpec;
	}

	protected static VirtualDeviceConfigSpec createDisk(String dsName, int cKey, long diskSizeKB, String diskMode) {
		VirtualDeviceConfigSpec diskSpec = new VirtualDeviceConfigSpec();
		diskSpec.setOperation(VirtualDeviceConfigSpecOperation.add);
		diskSpec.setFileOperation(VirtualDeviceConfigSpecFileOperation.create);

		VirtualDisk vd = new VirtualDisk();
		vd.setCapacityInKB(diskSizeKB);
		diskSpec.setDevice(vd);
		vd.setKey(cKey + INDEX_DISK);
		vd.setUnitNumber(0);
		vd.setControllerKey(cKey + INDEX_SCSICONTROLLER);

		VirtualDiskFlatVer2BackingInfo diskfileBacking = new VirtualDiskFlatVer2BackingInfo();
		String fileName = "[" + dsName + "]";
		diskfileBacking.setFileName(fileName);
		diskfileBacking.setDiskMode(diskMode);
		diskfileBacking.setThinProvisioned(true);
		vd.setBacking(diskfileBacking);
		return diskSpec;
	}
	
	protected static VirtualDevice getIDEController(VirtualMachine vm) throws Exception {
		VirtualDevice ideCtlr = null;
		VirtualDevice[] defaultDevices = getDefaultDevices(vm);
		for (int i = 0; i < defaultDevices.length; i++) {
			if (defaultDevices[i] instanceof VirtualIDEController) {
				ideCtlr = defaultDevices[i];
				break;
			}
		}
		return ideCtlr;
	}
	
	protected static VirtualDevice getIDEController(HostSystem host) throws RemoteException {
		VirtualDevice ideCtlr = null;
		VirtualDevice[] defaultDevices = getDefaultDevices(host);
		for (int i = 0; i < defaultDevices.length; i++) {
			if (defaultDevices[i] instanceof VirtualIDEController) {
				ideCtlr = defaultDevices[i];
				break;
			}
		}
		return ideCtlr;
	}

	protected static VirtualDevice[] getDefaultDevices(VirtualMachine vm) throws RemoteException {
		VirtualMachineRuntimeInfo vmRuntimeInfo = vm.getRuntime();
		ManagedObjectReference hmor = vmRuntimeInfo.getHost();
		HostSystem host = new HostSystem(vm.getServerConnection(), hmor);		
		return getDefaultDevices(host);
	}

	protected static VirtualDevice[] getDefaultDevices(HostSystem host) throws RemoteException {
		ComputeResource cr = (ComputeResource) host.getParent();
		EnvironmentBrowser envBrowser = cr.getEnvironmentBrowser();
		VirtualMachineConfigOption cfgOpt = envBrowser.queryConfigOption(null, host);
		VirtualDevice[] defaultDevs = null;

		if (cfgOpt == null) {
			throw new BuildException("No VirtualMachineConfigOption found in EnvironmentBrowser");
		} else {
			defaultDevs = cfgOpt.getDefaultDevice();
			if (defaultDevs == null) {
				throw new BuildException("No defaultDevs found in VirtualMachineConfigOption");
			}
		}
		return defaultDevs;
	}
	  
	protected static VirtualDeviceConfigSpec createNIC(int cKey, String netName, String nicName) {
		VirtualDeviceConfigSpec nicSpec = new VirtualDeviceConfigSpec();
		nicSpec.setOperation(VirtualDeviceConfigSpecOperation.add);

		VirtualEthernetCard nic = new VirtualPCNet32();
		VirtualEthernetCardNetworkBackingInfo nicBacking = new VirtualEthernetCardNetworkBackingInfo();
		nicBacking.setDeviceName(netName);

		Description info = new Description();
		info.setLabel(nicName);
		info.setSummary(netName);
		nic.setDeviceInfo(info);

		// type: "generated", "manual", "assigned" by VC
		nic.setAddressType("generated");
		nic.setBacking(nicBacking);
		nic.setKey(cKey + INDEX_NET);

		nicSpec.setDevice(nic);
		return nicSpec;
	}

	/**
	 * Getter for {@link VMCreateTask#resourcePool}.
	 */
	public String getResourcePool() {
		return resourcePool;
	}

	/**
	 * Setter for {@link VMCreateTask#resourcePool}.
	 */
	public void setResourcePool(String resourcePool) {
		this.resourcePool = resourcePool;
	}

	/**
	 * Getter for {@link VMCreateTask#template}.
	 */
	public String getTemplate() {
		return template;
	}

	/**
	 * Setter for {@link VMCreateTask#template}.
	 */
	public void setTemplate(String template) {
		this.template = template;
	}

	/**
	 * Getter for {@link VMCreateTask#datastore}.
	 */
	public String getDatastore() {
		return datastore;
	}

	/**
	 * Setter for {@link VMCreateTask#datastore}.
	 */
	public void setDatastore(String datastore) {
		this.datastore = datastore;
	}

	/**
	 * Getter for {@link VMCreateTask#datastoreType}.
	 */
	public String getDatastoreType() {
		return datastoreType;
	}

	/**
	 * Setter for {@link VMCreateTask#datastoreType}.
	 */
	public void setDatastoreType(String datastoreType) {
		this.datastoreType = datastoreType;
	}

	/**
	 * Getter for {@link VMCreateTask#vm}.
	 */
	public String getVm() {
		return vm;
	}

	/**
	 * Setter for {@link VMCreateTask#vm}.
	 */
	public void setVm(String vm) {
		this.vm = vm;
	}

	/**
	 * Getter for {@link VMCreateTask#wait}.
	 */
	public boolean isWait() {
		return wait;
	}

	/**
	 * Setter for {@link VMCreateTask#wait}.
	 */
	public void setWait(boolean wait) {
		this.wait = wait;
	}

	/**
	 * Getter for {@link VMCreateTask#powerOn}.
	 */
	public boolean isPowerOn() {
		return powerOn;
	}

	/**
	 * Setter for {@link VMCreateTask#powerOn}.
	 */
	public void setPowerOn(boolean powerOn) {
		this.powerOn = powerOn;
	}

	/**
	 * Getter for {@link VMCreateTask#cpu}.
	 */
	public int getCpu() {
		return cpu;
	}

	/**
	 * Setter for {@link VMCreateTask#cpu}.
	 */
	public void setCpu(int cpu) {
		this.cpu = cpu;
	}

	/**
	 * Getter for {@link VMCreateTask#memory}.
	 */
	public long getMemory() {
		return memory;
	}

	/**
	 * Setter for {@link VMCreateTask#memory}.
	 */
	public void setMemory(long memory) {
		this.memory = memory;
	}

	/**
	 * Getter for {@link VMCreateTask#guestType}.
	 */
	public String getGuestType() {
		return guestType;
	}

	/**
	 * Setter for {@link VMCreateTask#guestType}.
	 */
	public void setGuestType(String guestType) {
		this.guestType = guestType;
	}

	/**
	 * Getter for {@link VMCreateTask#diskSize}.
	 */
	public long getDiskSize() {
		return diskSize;
	}

	/**
	 * Setter for {@link VMCreateTask#diskSize}.
	 */
	public void setDiskSize(long diskSize) {
		this.diskSize = diskSize;
	}

	/**
	 * Getter for {@link VMCreateTask#diskMode}.
	 */
	public String getDiskMode() {
		return diskMode;
	}

	/**
	 * Setter for {@link VMCreateTask#diskMode}.
	 */
	public void setDiskMode(String diskMode) {
		this.diskMode = diskMode;
	}

	/**
	 * Getter for {@link VMCreateTask#deviceKey}.
	 */
	public int getDeviceKey() {
		return deviceKey;
	}

	/**
	 * Setter for {@link VMCreateTask#deviceKey}.
	 */
	public void setDeviceKey(int deviceKey) {
		this.deviceKey = deviceKey;
	}

	/**
	 * Getter for {@link VMCreateTask#netName}.
	 */
	public String getNetName() {
		return netName;
	}

	/**
	 * Setter for {@link VMCreateTask#netName}.
	 */
	public void setNetName(String netName) {
		this.netName = netName;
	}

	/**
	 * Getter for {@link VMCreateTask#nicName}.
	 */
	public String getNicName() {
		return nicName;
	}

	/**
	 * Setter for {@link VMCreateTask#nicName}.
	 */
	public void setNicName(String nicName) {
		this.nicName = nicName;
	}

	/**
	 * Getter for {@link VMCreateTask#annotation}.
	 */
	public String getAnnotation() {
		return annotation;
	}

	/**
	 * Setter for {@link VMCreateTask#annotation}.
	 */
	public void setAnnotation(String annotation) {
		this.annotation = annotation;
	}

	/**
	 * Getter for {@link VMCreateTask#iso}.
	 */
	public String getIso() {
		return iso;
	}

	/**
	 * Setter for {@link VMCreateTask#iso}.
	 */
	public void setIso(String iso) {
		this.iso = iso;
	}

	/**
	 * Getter for {@link VMCreateTask#bootOrder}.
	 */
	public String getBootOrder() {
		return bootOrder;
	}

	/**
	 * Setter for {@link VMCreateTask#bootOrder}.
	 */
	public void setBootOrder(String bootOrder) {
		this.bootOrder = bootOrder;
	}

	/**
	 * Getter for {@link VMCreateTask#floppyImage}.
	 */
	public String getFloppyImage() {
		return floppyImage;
	}

	/**
	 * Setter for {@link VMCreateTask#floppyImage}.
	 */
	public void setFloppyImage(String floppyImage) {
		this.floppyImage = floppyImage;
	}

	/**
	 * Getter for {@link VMCreateTask#customization}.
	 */
	public String getCustomization() {
		return customization;
	}

	/**
	 * Setter for {@link VMCreateTask#customization}.
	 */
	public void setCustomization(String customization) {
		this.customization = customization;
	}
}
