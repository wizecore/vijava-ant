package com.wizecore.ant.vijava;

import java.io.IOException;
import java.net.MalformedURLException;
import java.rmi.RemoteException;

import org.apache.tools.ant.BuildException;

import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.VirtualCdrom;
import com.vmware.vim25.VirtualCdromIsoBackingInfo;
import com.vmware.vim25.VirtualDevice;
import com.vmware.vim25.VirtualDeviceConfigSpec;
import com.vmware.vim25.VirtualDeviceConfigSpecOperation;
import com.vmware.vim25.VirtualMachineConfigSpec;
import com.vmware.vim25.mo.Datastore;
import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.VirtualMachine;
import com.vmware.vim25.mox.VirtualMachineDeviceManager;

/**
 * Requests power on of specified virtual machine.
 * 
 * @author huksley
 */
public class VMMountImageTask extends AbstractVMTask {

	String vm;
	boolean wait = false;
	String iso = null;
	boolean connected = true;
	int deviceKey = 1000;
	String datastoreType = "sanfs";	
	
	@Override
	public void execute() throws BuildException {
		super.execute();		

		try {
			ServiceInstance si = getServiceInstance();
			try {
				Folder rootFolder = si.getRootFolder();
				
				if (vm == null) {
					throw new BuildException("No vm specified!");
				}
				VirtualMachine target = (VirtualMachine) new InventoryNavigator(rootFolder).searchManagedEntity("VirtualMachine", vm);
				if (target == null) {
					throw new BuildException("Unknown virtual machine: " + vm);
				}

				if (iso == null) {
					throw new BuildException("No iso specified!");
				}
				
				VirtualCdrom cd = null;
				// Check for exiting
				VirtualMachineDeviceManager dm = new VirtualMachineDeviceManager(target);
				VirtualDevice[] devices = dm.getAllVirtualDevices();
				for (int i = 0; i < devices.length; i++) {
					if (devices[i].getBacking() instanceof VirtualCdromIsoBackingInfo) {
						cd = (VirtualCdrom) devices[i];
						break;
					}
				}
				VirtualDeviceConfigSpec[] deviceChange = cd != null ? new VirtualDeviceConfigSpec[2] : new VirtualDeviceConfigSpec[1];
				
				if (cd != null) {
					VirtualDeviceConfigSpec cdSpec = new VirtualDeviceConfigSpec();
					cdSpec.setOperation(VirtualDeviceConfigSpecOperation.remove);
					cdSpec.setDevice(cd);
					deviceChange[0] = cdSpec;
					log.info("Removing existing " + cd);
				}

				if (iso != null) {
					Datastore targetDatastore = null;
					ManagedObjectReference hmor = target.getRuntime().getHost();
					HostSystem targetHost = new HostSystem(si.getServerConnection(), hmor);
					Datastore[] ds = targetHost.getDatastores();
					for (int i = 0; i < ds.length; i++) {
						if (ds[i].getInfo().getUrl().startsWith(datastoreType + "://")) {
							targetDatastore = ds[i];
							break;
						}
					}
						
					Datastore isods = iso.startsWith("[") ? null : targetDatastore;
					String filename = iso;
					if (isods == null) {
						String dsName = iso.substring(1, iso.indexOf("]")).trim();
						isods = (Datastore) new InventoryNavigator(rootFolder).searchManagedEntity("Datastore", dsName);
					} else {
						filename = "[" + targetDatastore.getName() + "] " + iso;
					}
					
					log.info("Adding image " + filename);
					VirtualDeviceConfigSpec isoSpec = VMCreateTask.createISO(deviceKey, targetHost, isods, filename);
					if (cd != null) {
						deviceChange[1] = isoSpec;
					} else {
						deviceChange[0] = isoSpec;
					}					
				}
				
				VirtualMachineConfigSpec configSpec = new VirtualMachineConfigSpec();
				configSpec.setDeviceChange(deviceChange);				
				runTask(target.reconfigVM_Task(configSpec), wait);
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
	 * Getter for {@link VMMountImageTask#vm}.
	 */
	public String getVm() {
		return vm;
	}

	/**
	 * Setter for {@link VMMountImageTask#vm}.
	 */
	public void setVm(String vm) {
		this.vm = vm;
	}

	/**
	 * Getter for {@link VMMountImageTask#wait}.
	 */
	public boolean isWait() {
		return wait;
	}

	/**
	 * Setter for {@link VMMountImageTask#wait}.
	 */
	public void setWait(boolean wait) {
		this.wait = wait;
	}

	/**
	 * Getter for {@link VMMountImageTask#iso}.
	 */
	public String getIso() {
		return iso;
	}

	/**
	 * Setter for {@link VMMountImageTask#iso}.
	 */
	public void setIso(String iso) {
		this.iso = iso;
	}

	/**
	 * Getter for {@link VMMountImageTask#connected}.
	 */
	public boolean isConnected() {
		return connected;
	}

	/**
	 * Setter for {@link VMMountImageTask#connected}.
	 */
	public void setConnected(boolean connected) {
		this.connected = connected;
	}

	/**
	 * Getter for {@link VMMountImageTask#deviceKey}.
	 */
	public int getDeviceKey() {
		return deviceKey;
	}

	/**
	 * Setter for {@link VMMountImageTask#deviceKey}.
	 */
	public void setDeviceKey(int deviceKey) {
		this.deviceKey = deviceKey;
	}

	/**
	 * Getter for {@link VMMountImageTask#datastoreType}.
	 */
	public String getDatastoreType() {
		return datastoreType;
	}

	/**
	 * Setter for {@link VMMountImageTask#datastoreType}.
	 */
	public void setDatastoreType(String datastoreType) {
		this.datastoreType = datastoreType;
	}
}
