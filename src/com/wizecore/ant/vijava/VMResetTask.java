package com.wizecore.ant.vijava;

import java.io.IOException;

import org.apache.tools.ant.BuildException;

import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.VirtualMachine;


public class VMResetTask extends AbstractVMTask {

	String vm;
	boolean wait;
	
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
				
				log.info("Resetting VM " + vm);
				runTask(target.resetVM_Task(), wait);
			} catch (InterruptedException e) {
				throw new BuildException(e);
			} finally {
				closeServerInstance(si);
			}
		} catch (IOException e) {
			throw new BuildException(e);
		}
	}

	/**
	 * Getter for {@link VMResetTask#vm}.
	 */
	public String getVm() {
		return vm;
	}

	/**
	 * Setter for {@link VMResetTask#vm}.
	 */
	public void setVm(String vm) {
		this.vm = vm;
	}

	/**
	 * Getter for {@link VMResetTask#wait}.
	 */
	public boolean isWait() {
		return wait;
	}

	/**
	 * Setter for {@link VMResetTask#wait}.
	 */
	public void setWait(boolean wait) {
		this.wait = wait;
	}
}
