package com.wizecore.ant.vijava;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.TaskContainer;
import org.apache.tools.ant.UnknownElement;

import com.vmware.vim25.mo.ServiceInstance;

/**
 * Optional root for all virtual machine tasks.
 * 
 * @ant.task name="VMTask" category="vm"
 * @author huksley
 */
public class VMTask extends AbstractVMTask implements TaskContainer {
	List<AbstractVMTask> tasks = new ArrayList<AbstractVMTask>();
	
	@Override
	public void addTask(Task t) {
		if (t instanceof AbstractVMTask) {
			add((AbstractVMTask) t);
		} else
		if (t instanceof UnknownElement) {
			log.info("Ignoring " + t);
		} else {
			throw new BuildException("Not a vm task: " + t);
		}
	}
	
	public void add(AbstractVMTask t) {
		t.parent = this;
		tasks.add(t);
	}
	
	/*
	public void addConfigured(VMTask t) { add(t); }
	public void addConfigured(VMCreateTask t) { add(t); }
	public void addConfigured(VMMountImageTask t) { add(t); }
	public void addConfigured(VMPowerOffTask t) { add(t); }
	public void addConfigured(VMPowerOnTask t) { add(t); }
	public void addConfigured(VMRebootTask t) { add(t); }
	public void addConfigured(VMResetTask t) { add(t); }
	public void addConfigured(VMShutdownTask t) { add(t); }
	public void addConfigured(VMStandbyTask t) { add(t); }
	public void addConfigured(VMSuspendTask t) { add(t); }
	*/
	
	@Override
	public void execute() throws BuildException {
		super.execute();
		
		try {
			ServiceInstance si = getServiceInstance();
			try {
				for (int i = 0; i < tasks.size(); i++) {
					AbstractVMTask t = tasks.get(i);
					t.perform();
				}
			} finally {
				closeServerInstance(si);
			}
		} catch (IOException e) {
			throw new BuildException(e);
		}
	}
}
