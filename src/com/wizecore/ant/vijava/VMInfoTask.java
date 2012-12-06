package com.wizecore.ant.vijava;

import java.io.IOException;

import org.apache.tools.ant.BuildException;

import com.vmware.vim25.Event;
import com.vmware.vim25.EventFilterSpec;
import com.vmware.vim25.EventFilterSpecByEntity;
import com.vmware.vim25.EventFilterSpecRecursionOption;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.mo.Datastore;
import com.vmware.vim25.mo.EventManager;
import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.VirtualMachine;

/**
 * Assigns information from virtual machine to properties specified.
 * @ant.task name="vmInfo" category="vmInfo"
 * @author huksley
 */
public class VMInfoTask extends AbstractVMTask {

	String vm;	
	String ipaddress;
	String guestHostName;
	String datastore;
	String datastoreType = "sanfs";
	String lastEvent;
	
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
				
				if (ipaddress != null) {
					getProject().setProperty(ipaddress, target.getGuest().getIpAddress());
				}
				
				if (guestHostName != null) {
					getProject().setProperty(guestHostName, target.getGuest().getHostName());
				}
				
				if (datastore != null) {
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
					
					getProject().setProperty(datastore, targetDatastore.getName());
				}
				
				if (lastEvent != null) {
					EventManager evm = si.getEventManager();
					EventFilterSpec f = new EventFilterSpec();
					EventFilterSpecByEntity ef = new EventFilterSpecByEntity();
					ef.setEntity(target.getMOR());
					ef.setRecursion(EventFilterSpecRecursionOption.all);
					f.setEntity(ef);
					Event[] ev = evm.queryEvents(f);
					if (ev != null && ev.length > 0) {
						String cls = ev[ev.length - 1].getClass().getSimpleName();
						getProject().setProperty(lastEvent, cls);
					} else {
						getProject().setProperty(lastEvent, "");
					}
				}
			} finally {
				closeServerInstance(si);
			}
		} catch (IOException e) {
			throw new BuildException(e);
		}
	}

	/**
	 * Getter for {@link VMPowerOnTask#vm}.
	 */
	public String getVm() {
		return vm;
	}

	/**
	 * Virtual machine name to query for information.
	 * 
	 * @ant.required
	 */
	public void setVm(String vm) {
		this.vm = vm;
	}

	/**
	 * Getter for {@link VMInfoTask#ipaddress}.
	 */
	public String getIpaddress() {
		return ipaddress;
	}

	/**
	 * <b>Property name</b> to set to virtual machine IP address.
	 * @ant.not-required
	 */
	public void setIpaddress(String ipaddress) {
		this.ipaddress = ipaddress;
	}

	/**
	 * Getter for {@link VMInfoTask#guestHostName}.
	 */
	public String getGuestHostName() {
		return guestHostName;
	}

	/**
	 * <b>Property name</b> to set to virtual machine hostname.
	 * @ant.not-required
	 */
	public void setGuestHostName(String guestHostName) {
		this.guestHostName = guestHostName;
	}

	/**
	 * Getter for {@link VMInfoTask#datastore}.
	 */
	public String getDatastore() {
		return datastore;
	}

	/**
	 * <b>Property name</b> to set to virtual machine datastore.
	 * @ant.not-required
	 */
	public void setDatastore(String datastore) {
		this.datastore = datastore;
	}

	/**
	 * Getter for {@link VMInfoTask#datastoreType}.
	 */
	public String getDatastoreType() {
		return datastoreType;
	}

	/**
	 * Filter for datastores. Default is sanfs.
	 * @ant.not-required
	 */
	public void setDatastoreType(String datastoreType) {
		this.datastoreType = datastoreType;
	}

	/**
	 * @ant.not-required
	 */
	public String getLastEvent() {
		return lastEvent;
	}

	/**
	 * <b>Property name</b> to set to virtual machine last event.
	 * 
	 * @ant.not-required
	 */
	public void setLastEvent(String lastEvent) {
		this.lastEvent = lastEvent;
	}
}
