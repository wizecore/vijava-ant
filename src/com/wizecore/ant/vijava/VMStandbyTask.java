package com.wizecore.ant.vijava;

import java.io.IOException;

import org.apache.tools.ant.BuildException;

import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.VirtualMachine;


/**
 * Puts virtual machine in standby.
 * 
 * @ant.task name="vmStandby" category="vmStandby"
 * @author huksley
 */
public class VMStandbyTask extends AbstractVMTask {

	String vm;
	
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
				
				log.info("Standby VM " + vm);
				target.standbyGuest();
			} finally {
				closeServerInstance(si);
			}
		} catch (IOException e) {
			throw new BuildException(e);
		}
	}
	
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
}
