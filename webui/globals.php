<?php

abstract class jobType{
	const backupJob=0;
	const restoreJob=1;
	const agentBackup=2;
	const fileRestore=3;
	const reserved=4;			// used by the UI for event log messages
	const replicate=5;		// this is the VM Replicate job
	const rawBackup=6;		// these will involve an appliance or something
	const rawRestore=7;
	const offsiteJob=8;
	const system=10;			// used for purge jobs and the like
	const rawReplicate=14;		// 11 - 13 are already used in UI
	const validateVm=15;
	const rawRestoreToFile=16;
	const systemHidden=17;		// these jobs will not show in the activity or schedule page
}
abstract class scheduleType{
	const daily=0;
	const monthly=1;
	const oneOff=2;
	const weekly=3;
	const notScheduled=4;
}


class BackupVMInfo{
	public $vmID;
	public $uuid;
	public $siteID;
	public $hostID;
	public $srID;
	public $version;
	public $volumes = array();
	function __construct() {		
		$this->volumes = array();
		$this->vmID=0;
		$this->siteID=0;
		$this->hostID=0;
		$this->srID=0;
		$this->version=0;
		$this->uuid="";
	}
}


?>
