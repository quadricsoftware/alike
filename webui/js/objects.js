JobType = { 
    backup:0,
	restore:1,
//	agentBackup:2,
    fileRestore:3,
    reserved:4,
	replicate:5,
//	rawBackup:6,
//	rawRestore:7,
	offsite:8,
	system:10,
 //   rawReplicate : 14,
    validateVm : 15,
    rawRestoreToFile:16,
    systemHidden:17
}

JobStatus = {
    pending:0,
    paused:1,
    active:2,
    activeWithErrors:3,
    errored:4,
    cancelled:5,
    complete:6,
    failed:7,
    warning:8
}
SnapshotType = {
    standard : 0,
    quiesced : 1,
    diskAndMemory : 2,
    guestFallback : 3,
    noSnapshot : 9
}

PolicyJobType = {
    none : 0,
    host : 1,
    name : 2,
    tag : 3
}
VMState = {
    Halted : 0,
    Paused : 1,
    Running : 2,
    Suspended : 3,
    unknown : 4
}
VirtType = {
    manual:0,
	vmware:1,		
	xen:2,
	hyperv:3,
	marathon:4,
    marathonBlockedOps:5,
    physical : 10
}
EditionType = {
    Free : 0,
    Standard : 1,
    Beta : 2,
    DR : 3,
    MSP_Standard : 4,
    MSP_DR : 5
}
FlavorType = {
    Commercial : 0,
    NFR : 1,
    InternalUse : 2,
    FileLevel : 3,
    Phsyical : 10    
}
VaultStatus = {
    internalUse : -1,
    committed : 1,
    uncommitted : 2,
    ambInProcess : 3,
    vaultNeedPrints : 4,
    vaultSend : 5,
    vaultValidate : 7,
    vaultComplete : 6,
    purgedOnlyOnsite : -2,
    purgedOnlyOffsite : -3
}
VmFileStatus = {
    vmFileStatusUncommitted : 0,
    vmFileStatusCommitted : 1,
    vmFileStatusValidatedOnsite : 2,
    vmFileStatusValidatedOffsite : 3,
    mvFileStatusValidatedBoth : 4,
    vmFileStatusCorruptedOnsite : -1,
    vmFileStatusCorruptedOffsite : -2,
    vmFileStatusCorruptedBoth : -3
}
VmAction= {
    vmActionNone : 0,
    vmActionRetainOffsite : 1,
    vmActionRetainOnsite : 2,
    vmActionRetainAll : 3,
    vmActionPurgeOffsite : -1,
    vmActionPurgeOnsite : -2,
    vmActionPurgeAll : -3
}
JobEntryStatus = { 
    ok : 0, 
    failed:1, 
    warning:2, 
    active:3, 
    trace:4
}
ScheduleType = { 
    daily: 0, 
    monthly:1, 
    solo:2, 
    restore:3, 
    system:4 
}
SnapshotType= {
    standard : 0,
    quiesced : 1,
    diskAndMemory : 2,
    guestFallback : 3,
    noSnapshot : 9      
}
PolicyJobType= {
    none : 0,
    host : 1,
    name : 2,
    tag : 3
}
OSVStatus ={
    remoteStatusJavaInstallVersion : -9,
    errorInvalidStructure : -8,
    errorBlockSizeMismatch : -7,
    errorBrokenSync : -6,
    errorDiskFull : -5,
    errorInternalError : -4,
    failedLogin : -3,
    errorVersionMismatch : -2,
    errorNoConnection : -1,
    OkIdle : 0,
    OkBaselining : 1,
    OkXfer : 2,
    OkValidating : 3,
    OkComplete : 4,
    OkReadOnly : 5,
    OkNoPair : 6
}
