package com.wizecore.ant.vijava;

import java.io.IOException;

import org.apache.tools.ant.BuildException;

import com.vmware.vim25.Event;
import com.vmware.vim25.EventFilterSpec;
import com.vmware.vim25.EventFilterSpecByEntity;
import com.vmware.vim25.EventFilterSpecRecursionOption;
import com.vmware.vim25.VirtualMachinePowerState;
import com.vmware.vim25.VirtualMachineToolsStatus;
import com.vmware.vim25.mo.EventManager;
import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.VirtualMachine;

/**
 * Waits for some event for specified virtual machine.
 * 
 * @ant.task name="vmWait" category="vmWait"
 * @author huksley
 */
public class VMWaitTask extends AbstractVMTask {
	String vm;	
	long timeout = 300000;
	String event = null;
	boolean poweredOn;
	boolean poweredOff;
	boolean haveIpAddress;
	boolean boot;
	long checkTime = 2000;
	String guestState;
	String success;
	String failed;
	
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
				
				EventManager evm = si.getEventManager();
				
				long started = System.currentTimeMillis();
				boolean allMet = false;
				while (!allMet) {
					allMet = true;
					
					if (allMet && event != null) {
						EventFilterSpec f = new EventFilterSpec();
						EventFilterSpecByEntity ef = new EventFilterSpecByEntity();
						ef.setEntity(target.getMOR());
						ef.setRecursion(EventFilterSpecRecursionOption.all);
						f.setEntity(ef);
						Event[] ev = evm.queryEvents(f);
						boolean found = false;
						for (int i = 0; i < ev.length; i++) {
							String cls = ev[i].getClass().getSimpleName();
							if (cls.equals(event)) {
								found = true;								
								break;
							}
						}
						allMet = allMet && found;
						if (allMet) {
							log.info("Got event " + event);
						}
					}
					
					if (allMet && poweredOn) {
						allMet = allMet && target.getRuntime().getPowerState() == VirtualMachinePowerState.poweredOn;
						if (allMet) {
							log.info("Got poweredOn");
						}
					}
					
					if (allMet && poweredOff) {
						allMet = allMet && target.getRuntime().getPowerState() == VirtualMachinePowerState.poweredOff;
						if (allMet) {
							log.info("Got poweredOff");
						}
					}
					
					if (allMet && haveIpAddress) {
						String ip = target.getGuest().getIpAddress();
						allMet = allMet && (ip != null && !ip.equals(""));
						if (allMet) {
							log.info("Got ip " + ip);
						}
					}
					if (allMet && boot) {
						VirtualMachineToolsStatus st = target.getGuest().getToolsStatus();
						allMet = allMet && (st == VirtualMachineToolsStatus.toolsOk || st == VirtualMachineToolsStatus.toolsOld);
						if (allMet) {
							log.info("Boot complete (tools running)");
						}
					}
					
					if (allMet && guestState != null) {
						String st = target.getGuest().getGuestState();
						allMet = allMet && (st.equalsIgnoreCase(guestState.trim()));
						if (allMet) {
							log.info("Guest state " + st);
						}
					}
					
					if (System.currentTimeMillis() - started > timeout) {
						if (failed != null) {
							getProject().setProperty(failed, "true");
						} else {
							throw new BuildException("Timed out waiting for events!");
						}
					}
					
					if (!allMet) {
						Thread.sleep(checkTime);
					}
				}
				
				if (success != null) {
					getProject().setProperty(success, "true");
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
	 * Getter for {@link VMShutdownTask#vm}.
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
	 * Getter for {@link VMWaitTask#timeout}.
	 */
	public long getTimeout() {
		return timeout;
	}

	/**
	 * Total wait timeout. Specified in milliseconds. Default is 5 minutes.
	 */
	public void setTimeout(long timeout) {
		this.timeout = timeout;
	}

	/**
	 * Getter for {@link VMWaitTask#event}.
	 */
	public String getEvent() {
		return event;
	}

	/**
	 * Wait for VM for specified event to occur. For example <code>CustomizationSucceeded</code>.
	 */
	public void setEvent(String event) {
		this.event = event;
	}

	/**
	 * Getter for {@link VMWaitTask#poweredOn}.
	 */
	public boolean isPoweredOn() {
		return poweredOn;
	}

	/**
	 * Wait for VM to power on.
	 */
	public void setPoweredOn(boolean poweredOn) {
		this.poweredOn = poweredOn;
	}

	/**
	 * Getter for {@link VMWaitTask#haveIpAddress}.
	 */
	public boolean isHaveIpAddress() {
		return haveIpAddress;
	}

	/**
	 * Wait for VM to obtain IP address on first adapter.
	 */
	public void setHaveIpAddress(boolean haveIpAddress) {
		this.haveIpAddress = haveIpAddress;
	}

	/**
	 * Getter for {@link VMWaitTask#checkTime}.
	 */
	public long getCheckTime() {
		return checkTime;
	}

	/**
	 * Sleep period. Default is 2000.
	 */
	public void setCheckTime(long checkTime) {
		this.checkTime = checkTime;
	}

	/**
	 * Getter for {@link VMWaitTask#success}.
	 */
	public String getSuccess() {
		return success;
	}

	/**
	 * <b>Property name</b> to set to <code>true</code> when wait was successfull.
	 */
	public void setSuccess(String success) {
		this.success = success;
	}

	/**
	 * Getter for {@link VMWaitTask#failed}.
	 */
	public String getFailed() {
		return failed;
	}

	/**
	 * <b>Property name</b> to set to <code>true</code> when wait have timed out.
	 */
	public void setFailed(String failed) {
		this.failed = failed;
	}

	/**
	 * Getter for {@link VMWaitTask#poweredOff}.
	 */
	public boolean isPoweredOff() {
		return poweredOff;
	}

	/**
	 * Waiting for VM to change state to poweredOff.
	 */
	public void setPoweredOff(boolean poweredOff) {
		this.poweredOff = poweredOff;
	}

	/**
	 * Getter for {@link VMWaitTask#boot}.
	 */
	public boolean isBoot() {
		return boot;
	}

	/**
	 * Wait for VM boot. This actually waits for VMWare Tools service to start. 
	 * Other services might be not started.
	 */
	public void setBoot(boolean boot) {
		this.boot = boot;
	}

	/**
	 * Getter for {@link VMWaitTask#guestState}.
	 */
	public String getGuestState() {
		return guestState;
	}

	/**
	 * Wait for specified guest state. See <a href="http://pubs.vmware.com/vi3/sdk/ReferenceGuide/vim.vm.GuestInfo.html">list</a> of possible values.
	 */
	public void setGuestState(String guestState) {
		this.guestState = guestState;
	}
}
