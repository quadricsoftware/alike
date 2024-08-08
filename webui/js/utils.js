// Entrypoint
function checkCurrentSession(){
	var url = window.location.href;
	var arr = url.split("/");
	var connectedHost = arr[0]+"//"+arr[2];
	window.alikeSite = connectedHost;
	wsCall("settings",null, "setWsSettings",null);
	pruneStorage(20);	// clean out old sessionStorage
	drawDash();
	clearMetaCaches();
	buildMetaCaches();
}

function setCookie(cname, cvalue, exdays) {
    var d = new Date();
    d.setTime(d.getTime() + (exdays*24*60*60*1000));
    var expires = "expires="+d.toUTCString();
    document.cookie = cname + "=" + cvalue + "; " + expires;
}
function setCookieSec(cname, cvalue, d) {
    var expires = "expires="+d.toUTCString();
    document.cookie = cname + "=" + cvalue + "; " + expires;
}
function getCookie(cname) {
    var name = cname + "=";
    var ca = document.cookie.split(';');
    for(var i=0; i<ca.length; i++) {
        var c = ca[i];
        while (c.charAt(0)==' ') c = c.substring(1);
        if (c.indexOf(name) == 0) return c.substring(name.length, c.length);
    }
    return "";
}

function setWsSettings(data){
	window.wsTimeout = data.wsTimeout;
	window.subInfo = data.sub;
	window.nodeMode = data.mode;	
	if(!data.settings.managerIP){
		let hostname = new URL(window.location.href).hostname;
		updateSetting("managerIP", hostname, 0)
	}
	if(!data.settings.welcomeShown){
		showWelcomeModal();
		var args = new Object();
		args["setting"]= "welcomeShown";
		args["value"]= 1;
		wsCall("setting/update", args, null,null, 0 );
	}
}

function setLanguage(lang){
    setCookie("language", lang, 9000);
}
function getLanguage(){
    if(window.language){ return window.language; }
    else {
        var lang = getCookie("language");        
        if(lang){ 
            window.language = lang;
        }
        else{ window.language = "en"; }
    }
	return window.language;
}

function setTimeFmt(fmt){
    setCookie("timeformat", fmt, 9000);
    window.timefmt = fmt;
}
function getTimeFmt(){
    if(window.timefmt){ return window.timefmt; }
    else {
        var tf = getCookie("timeformat");        
        if(tf){ 
            window.timefmt = tf;
        }
        else{ 
		    window.timefmt = "12";
	}
            return window.timefmt; 
    }
}

function clearTimer(){
    if(window.intervalTimerID){
        clearInterval(window.intervalTimerID);
    }
    if(window.jldTimer){
        clearTimeout(window.jldTimer);
    }
    if(window.a3NetTimer){
        clearTimeout(window.a3NetTimer);
    }
}
function setStartingUp(){
    if(window.isConnected ==true){
        $('#serviceState').addClass('service-state-unknown');    
        addPopup(getText('lost-cn-alike'));
        window.isConnected=false;
        showNoJobs();
        clearTimer();
	showStartingUp();
    }
}
function snipString(input, size){
    if(input.length > size){ 
	   input = input.substring(0,size-3);
       input += "...";
    }
    return input;
}

function ipCheck(ip){
 if (/^(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$/.test(ip)){
    return true;  
 }
    return false;
} 

function isNumeric(n){
    return !isNaN(parseFloat(n)) && isFinite(n);
}


function getScheduleTime(s){
    var out = "";
    if(s.scheduleType ==0){
        out += getText('daily');
    }else if(s.scheduleType==1){
        out += getText('monthly');
    }else if(s.scheduleType==4){
        return "N/A";
    }else{
        out += "On "+getDateCust(s.timestamp,false);
    }
	var d= new Date(0);
        d.setUTCSeconds(s.timestamp);

    var min = d.getMinutes();
    if(min <=9){
        min = '0'+min;
    }
	var hr = d.getHours();
    if(getTimeFmt()=="12"){
        var suf = "am";
        if(hr > 12) { 
            hr = hr-12;
            suf = "pm";
        }
        out += " at "+hr+":"+min+" "+suf;
    }else{
        out += " at "+d.getHours()+":"+min;
    }
    return out;
}

function getSchedNext(s){
    if(s.scheduleType==4){
        return "N/A";
    }else if(s.timestamp < 60000000){ return getText('never'); }
    
    
    var now = new Date();
    var date = new Date(0);
    date.setUTCSeconds(s.timestamp);
    // daily, check days + time
    // monthly, check day + time
    // one time, pretty much return as is
    
    if(s.scheduleType ==0){ // Daily        
	if(s.days.length ==0){ return "Never"; }
        var d = now.getDay();
        var nowSec= (now.getHours()*3600) + (now.getMinutes() * 60);
        var startSec = (date.getHours() * 3600) + (date.getMinutes()* 60);
                
        if(inArr(d, s.days) ){            
            if(date.getTime() > now.getTime()){
                return date;
            }else if(s.occurances > 1){                
                if( nowSec < (startSec + (s.occurances * s.interval ) ) ){        
                    var diff = (startSec - nowSec) * -1;
                    var intToAdd = Math.ceil(diff / s.interval);
                    var newd = new Date(date);
                    newd.setFullYear(now.getFullYear());
                    newd.setDate(now.getDate());
                    newd.setMonth(now.getMonth());
                    newd.setHours(date.getHours() + ((intToAdd * s.interval) /3600) );
                    
                    return newd;
                }
            }
         }
         now.setHours(date.getHours());
         now.setMinutes(date.getMinutes() );
         now.setSeconds(date.getSeconds());
         var dd = d;
         if(nowSec > startSec){
            dd++;
         }
         var next = s.days[0];         
         for(var i=dd; i< 7; i++){
            if(inArr(i,s.days)){
                next =i;
                break;
            }
         }
         var add = 0;
         if(next < d){ add= 7 - (d - next); }
         else{ add = next - d; }
         var newd = new Date(now);
         newd.setDate(now.getDate()+add);
         return newd;
    }else if(s.scheduleType==1){    // monthly        
        if(now.getDate() >date.getDate()){
		date.setDate(date.getDate()+1);
        }else{
		date.setDate(date.getDate());
        }
        return date;
    }else{
        return date;
    }
    
}

function getTime(epoch){
    if(epoch ==0){ return "N/A"; }
    date = new Date(0);
    date.setTime(getJSEpoch(epoch ));
    var h = date.getHours();
    if(h < 10){ h = '0'+h; }
    var m = date.getMinutes();
    if(m < 10){ m = '0'+m; }
    var out = h+":"+m;
    if(getTimeFmt()=="12"){
        var suffix = (h >= 12)? 'pm' : 'am';
        //only -12 from hours if it is greater than 12 (if not back at mid night)
        var h = (h > 12)? h -12 : h;
        //if 00 then it is 12 am
        h = (h == '00')? 12 : h;
        out = h+":"+m+" "+suffix;
    }
    return out;
}
function getDate(epoch, showTime=true){
    if(epoch ==0){ return "N/A"; }
    date = new Date(0);
    date.setTime(getJSEpoch(epoch ));
    var out = date.toLocaleDateString();
    if(showTime){
		out += " "+getTime(epoch);
	}
    //var out = (date.getMonth()+1) +"/"+date.getDate()+"/"+date.getFullYear() +" "+getTime(epoch);
    return out;
}

function getDateCust(epoch, showTime){
    date = new Date(0);
    date.setTime(getJSEpoch(epoch));
    var month = date.getMonth()+1;
    if(month <=9){
        month = '0'+month;
    }
    var day = date.getDate();
    if(day <=9){
        day = '0'+day;
    }
    var out = month+"/"+day;
    if(showTime){
        //out +="/"+date.getFullYear()+" "+date.getHours()+":"+date.getMinutes();
        out +="/"+date.getFullYear()+" "+getTime(epoch);
    }
    return out;
}

function getJSEpoch(epoch) {
//    if(epoch < 2000000000){
 //       date.setUTCSeconds(epoch * 1000);
        //date.setTime(epoch * 1000);
 //   }else{
        //date.setTime(epoch);
	var date = new Date(0);
	date.setUTCSeconds(epoch);
 //   }
    return date.getTime();
}

function timeUntil(date){
    var seconds = Math.floor((new Date() - date) / 1000);
    var interval = Math.floor(seconds / 31536000);
    if (interval > 1) {
        return interval + " "+getText('yearUnits')+" "+getText('ago');
    }
    interval = Math.floor(seconds / 2592000);
    if (interval > 1) {
        return interval + " "+getText('monthUnits')+" "+getText('ago');
    }
    interval = Math.floor(seconds / 86400);
    if (interval > 1) {
        return interval + " "+getText('dayUnits')+" "+getText('ago');
    }
    interval = Math.floor(seconds / 3600);
    if (interval > 1) {
        return interval + " "+getText('hourUnits')+" "+getText('ago');
    }
    interval = Math.floor(seconds / 60);
    if (interval > 1) {
        return interval + " mins "+getText('ago');
    }
    return Math.floor(seconds) + " "+getText('secUnits')+" "+getText('ago');
}

function timeSince(ts) {
	const now = Math.floor(Date.now() / 1000);
	const timeDiff = now - ts;

	const units = {
		year: 365 * 24 * 60 * 60,
		month: 30 * 24 * 60 * 60,
		week: 7 * 24 * 60 * 60,
		day: 24 * 60 * 60,
		hour: 60 * 60,
		min: 60,
		second:45 
	};

	for (const [unit, value] of Object.entries(units)) {
		if (timeDiff >= value) {
			const timeAgo = Math.floor(timeDiff / value);
			return `${timeAgo} ${unit}${timeAgo !== 1 ? "s" : ""} ago`;
		}
	}
	return "Just now";
}
function formatHours(hours) {
	const timeUnits = [
		{ unit: 'year', div: 8760 },  // 1 year = 8760 hours
		{ unit: 'month', div: 730 },  // 1 month = 730 hours (approximate)
		{ unit: 'week', div: 168 },   // 1 week = 168 hours
		{ unit: 'day', div: 24 },     // 1 day = 24 hours
		{ unit: 'hour', div: 1 }      // 1 hour = 1 hour (base unit)
	  ];

  for (const { unit, div } of timeUnits) {
    if (hours >= div) {
      const value = Math.floor(hours / div);
      return `${value} ${unit}${value !== 1 ? 's' : ''}`;
    }
  }

  return `${hours} hour${hours !== 1 ? 's' : ''}`;
}


function getScheduleVmSummaryText(sched){
    var output="";
    if(sched.Options.policyType != PolicyJobType.none){
        output = "<b>Policy Job</b>: ";
        if(sched.Options.policyType == PolicyJobType.host){
            output += "All Guests on defined host(s) ";
        }else if(sched.Options.policyType == PolicyJobType.name){
            output += "Search filter: "+sched.Options.policyFilterString;
        }else if(sched.Options.policyType == PolicyJobType.tag){
            output += "Xen Tag filter: "+sched.Options.policyFilterString;
        }
    }else{
        if(sched.VMs.length == 0){
            output = "<u>No systems selected</u>";
        }else if(sched.VMs.length < 4){
            jQuery.each(sched.VMs, function(i,vm){
		        output += vm.name;
		        if(i != sched.VMs.length-1){
		            output +=", ";
		        }
            });
        }else{
            output = sched.VMs.length +" Systems in job";
        }
    }
    return output;
}

function getRetention(stat){
	if(!Number.isInteger(stat)){ stat = Number(stat); } 
    switch(stat){
        case 4:
            return "Yearly";
            break;
        case 3:
            return "Monthly";
            break;
        case 2:
            return "Weekly";
            break;
        case 1:
            return "Daily";
            break;
        case 0:
            return "Basic";
            break;
        default:
            return "Unknown";
    }
}


// status (0 = success, 1=failed, 2=in progress,3=not started, 4= unknown)
function getVMJLDState(stat){
	if(!Number.isInteger(stat)){ stat = Number(stat); } 
    switch(stat){
        case 3:
            return "jld_stat jld_stat_pending";
            break;
        case 2:
            return "jld_stat jld_stat_active";
            break;
        case 1:
            return "jld_stat jld_stat_failed";
            break;
        case 0:
            return "jld_stat jld_stat_complete";
            break;
        default:
            return "jld_stat jld_stat_active";
    }
}

function getStateClassOld(stat){
	if(!Number.isInteger(stat)){ stat = Number(stat); } 
    switch(stat){
        case JobEntryStatus.ok:
            return "status-pill status-ok";
            break;
        case JobEntryStatus.failed:
            //return "status-failed";
            return "status-pill status-failed";
            break;
        case JobEntryStatus.warning:
            //return "badge badge-warning";
            return "status-pill status-errored";
            break;
        case JobEntryStatus.trace:
            return "status-pill status-trace";
        case JobEntryStatus.active:
            return "status-pill status-active";
            //return "status-trace";
            break;
    }
}

function getStatusClass(stat){
	if(!Number.isInteger(stat)){ stat = Number(stat); } 
    switch(stat){
        case JobStatus.pending:
            return "status-pill status-pending";
            break;
        case JobStatus.paused:
            return "status-paused";
            break;
        case JobStatus.active:
            //return "status-active";
            return "status-pill status-active";
            break;
        case JobStatus.activeWithErrors:
            return "status-pill status-active-errors";
            break;
        case JobStatus.warning:
            return "status-pill status-complete-warning";
            break;
        case JobStatus.errored:
		return "bg-danger";
            //return "status-errored";
            return "status-pill status-errored";
            break;
        case JobStatus.cancelled:
            return "status-pill status-cancelled";
            break;
        case JobStatus.complete:
            //return "status-default";
            return "status-pill status-ok";
            break;                        
        case JobStatus.failed:
            return "status-pill status-failed";
            break;
    }
}

function getSyslogClass(stat){
	var cls = "badge";
	if(stat > 6){ cls += " badge-secondary"; }
	else if(stat > 5){ cls += " badge-success"; }
	else if(stat > 4){ cls += " badge-primary"; }
	else if(stat > 3){ cls += " badge-warning"; }
	else if(stat > 2){ cls += " badge-danger"; }
	else { cls += " badge-danger"; }

	return cls;
}

function getStatusDesc(stat){
	if(!Number.isInteger(stat)){ stat = Number(stat); } 
    switch(stat){
        case JobStatus.pending:
            return getText('jstat-pend');
            break;
        case JobStatus.paused:
            return getText('jstat-pause');
            break;
        case JobStatus.active:
            return " Active ";
            break;
        case JobStatus.activeWithErrors:
            return "<i class='fa fa-exclamation-triangle' style='color:#fff'></i>  Active &nbsp;";
            break;
        case JobStatus.warning:
            return "<i class='fa fa-exclamation-triangle' style='color:#ffc107'></i>  Notice &nbsp;";
            break;
        case JobStatus.errored:
            return " Error ";
            break;
        case JobStatus.cancelled:
            return getText('jstat-canc');
            break;
        case JobStatus.complete:
            return getText('jstat-done');
            break;                        
        case JobStatus.failed:
            return getText('jstat-fail');
            break;
    }
}

function getJobTypeDesc(type){
	if(!Number.isInteger(type)){ type = Number(type); } 
    switch(type){
        case 100:
            return getText('type-back');
            break;
        case 101:
            return getText('type-rest');
            break;
        case JobType.backup:
            return getText('type-back');
            break;
        case JobType.restore:
            return getText('type-rest');
            break;
        case JobType.agentBackup:
            return getText('type-qhbback');
            break;
        case JobType.fileRestore:
            return getText('type-flr');
            break;
        case JobType.replicate:
            return getText('type-rep');
            break;
        case JobType.rawBackup:
            return getText('type-enhback');
            break;
        case JobType.rawRestore:
            return getText('type-rest');
            break;
        case 200:
            return getText('type-vault');
            break;
        case 201:
            return "Reverse Vault";
            break;
        case JobType.offsite:
            return getText('type-vault');
            break;
        case JobType.system:
            return getText('type-sys');
            break;
        case JobType.validateVm:
            return getText('type-validate');
            break;
        case JobType.rawRestoreToFile:
            return getText('type-vhd'); 
            break;
        case JobType.rawReplicate:
            return getText('type-rep');
            break;
        default:
            return "Unknown Job Type: "+type;            
    }   
}

function getJobLogEntryStatusDesc(stat){
	if(!Number.isInteger(stat)){ stat = Number(stat); } 
    switch(stat){
        case JobEntryStatus.ok:
            return getText('stat-ok');
            break;
        case JobEntryStatus.failed:
            return getText('stat-fail');
            break;
        case JobEntryStatus.warning:
            return getText('stat-warn');
            break;
        case JobEntryStatus.active:
            return "Active";
            break;
        case JobEntryStatus.trace:
            return getText('stat-trace');
            break;
        default:
            return "Unknown status: "+stat;            
    }
}

function getPowerState(s){
    if(s=="0" || s == "halted"){
        return "<img src='/images/vm-stopped.png' title='VM is Stopped'>";
    }else if(s == "1" || s== "paused"){
        return "<img src='/images/vm-paused.png' title='VM is Paused'>";
    }else if(s == "2" || s== "running"){
        return "<img src='/images/vm-running.png' title='VM is Running'>";
    }else if(s =="3"){
        return "<img src='/images/vm-paused.png' title='VM is Paused'>";
    }else if(s =="4" || s=="unknown"){
        return "<img src='/images/vm-unknown.png' title='VM power state is unknown'>";
    }else if(s =="6"){
        return "<img src='/images/vm-paused.png' title='VM is Paused'>";
    }else if(s === null || s == ""){
        return "<img src='/images/vm_valid_icon.png'>";
    }else{
        return "<img src='/images/vm_valid_icon.png' title='VM power state is "+s+"'>";
	}
}

function getOsVersion(str){
    if(str){
        var pos = str.indexOf("XenServer");
        if(pos >= 0){
            var xs= str.substring(pos + 10);
            var pos2 = xs.indexOf("(");
            if(pos2 >=0){
                return "("+ xs.substr(0,pos2)+")";
            }else{
                return "("+xs+")"
            }
        }    
    }
    return "";
}

function getPlatTypeIcon(type){
	if(typeof type === "string" && type.includes("XCP")){ 
	    return "<img src=\"/images/xcp_icon.png\" title='"+type+"'>";
	}
	if(!Number.isInteger(type)){ type = Number(type); }

    var t = getVirtTypeText(type);
    switch(type){
        case 2:
            return "<img src=\"/images/platicon-xen.png\" title='"+t+"'>";
        case 3:
            return "<img src=\"/images/platicon-hv.png\" title='"+t+"'>";
        case 10:
            return "<img src=\"/images/platicon-phy.png\" title='"+t+"'>";
    }
}
function getVirtTypeIcon(type){
	if(typeof type === "string" && type.includes("XCP")){ 
	    return "<img src=\"/images/xcp_icon.png\" title='"+type+"'>";
	}
	if(!Number.isInteger(type)){ type = Number(type); }
    var t = getVirtTypeText(type);
    switch(type){
        case 2:
            return "<img src=\"/images/xen-icon_new.png\" title='"+t+"'>";
        case 22:
            return "<img src=\"/images/xcp_icon.png\" title='"+t+"'>";
        case 3:
            return "<img src=\"/images/hyperv-icon-tiny.png\" title='"+t+"'>";
        case 10:
            return "<img src=\"/images/windowsflag.png\" title='"+t+"'>";
        case -1:
            return "<img src=\"/images/folder_icon.png\" title=\"Folder\">";
	default:
            return "<img src=\"/images/help.png\" alt='"+t+"'>";
    }
}
function getVirtTypeText(type){
	if(!Number.isInteger(type)){ type = Number(type); }
    switch(type){
        case 2:
            return getText('plat-xen');
        case 3:
            return getText('plat-hv');
        case 10:
            return getText('plat-phys');
        case 22:
            return 'XCP-ng';
        default:
            return "Unknown Type";
    }
}
function getJobTypeIcon(type){
	if(!Number.isInteger(type)){ type = Number(type); }

    if(type== 0 || type==2 || type == 6){        
            return "<img src=\"/images/jobicon-backup.png\" alt=\"Backup job\">";
    }else if(type == 1 || type == 3 || type == 7 || type == 16){
            return "<img src=\"/images/jobicon-restore.png\" alt=\"Restore job\">";
    }else if(type == 5 || type == 14 ){
            return "<img src=\"/images/jobicon-restore.png\" alt=\"Replicate job\">";
    }else if (type ==8){
            return "<img src=\"/images/jobicon-vault.png\" alt=\"Offsite Vault job\">";
    }else {
            return "<img src=\"/images/jobicon-system.png\" alt=\"System Vault job\">";
    }
}
function getNotifyIcon(s){
	if(!Number.isInteger(s)){ s = Number(s); }
    var img = "<div class=\"notice-info\"></div>";
    if(s==2){
        img = "<div class=\"notice-warn\"></div>";
    }else if(s==1){
        img = "<div class=\"notice-error\"></div>";
    }
    return img;
}

// 0=good, 1=corrupt, 2=validating, 3=committing
function getFileStatus(type){
    var out = 3;
    if(type== VmFileStatus.vmFileStatusCorruptedOnsite || type==VmFileStatus.vmFileStatusCorruptedOffsite || type == VmFileStatus.vmFileStatusCorruptedBoth){            
            out=1;
    }else if(type == VmFileStatus.vmFileStatusCommitted){
            out=2;
    }else if(type == VmFileStatus.vmFileStatusUncommitted ){
            out=3;
    }else {
            out=0;
    }
    return out;
}

function getFileStatusIcon(type){
    var s = getFileStatus(type);
    var img = "vm_commit_icon.png";
    if(s==1){
            img = "vm_corrupt_icon.png";
    }else if(s==2){
            img = "vm_validating_icon.png";
    }else if(s==3){
            img = "vm_commit_icon.png";
    }else {
            img = "vm_validated_icon.png";
    }
    return "<img src=\"/images/"+img+"\" title=\""+getFileStatusText(s)+"\">";
}
function getFileStatusText(s){
    if(s==1){
            return "Backup contains corrupt data!";
    }else if(s==2){
            return "Backup data validation in progress";
    }else if(s==3){
            return "Backup data being committed";
    }else {
            return "Backup data validated successfully!";
    }
}
function getBackupStatusIcon(s){
    
    var img = "validate-complete-icon.png";
    if(s==1){
            img = "validate-fail-icon.png";
    }else if(s==2){
            img = "validating-icon.png";
    }else if(s==3){
            img = "committed-icon.png";
    }
    return "<img src=\"/images/"+img+"\" title=\""+getFileStatusText(s)+"\">";
}

//function getFileStatusText(type){

//    switch(type){
//        case VmFileStatus.vmFileStatusUncommitted:
//            return "Backup data being committed";
//            break;
//        case VmFileStatus.vmFileStatusCommitted:
//            return "Backup data validation in progress";
//            break;
//        case VmFileStatus.vmFileStatusValidatedOnsite:
//            return "Backup data validated successfully!";
//            break;
//        case VmFileStatus.vmFileStatusValidatedOffsite:
//            return "Backup data validated successfully!";
//            break;
//        case VmFileStatus.mvFileStatusValidatedBoth:
//            return "Backup data validated successfully!";
//            break;
//        case VmFileStatus.vmFileStatusCorruptedOnsite:
//            return "Backup contains corrupt data!";
//            break;
//        case VmFileStatus.vmFileStatusCorruptedOffsite:
//            return "Backup contains corrupt data!";
//            break;
//        case VmFileStatus.vmFileStatusCorruptedBoth:
//            return "Backup contains corrupt data!";
//            break;
//        default:
//            return "Unknown status: "+type;    
//    }
//}
//
function getSubStatusStr(st){
	if(st ==1){
		return "Active";
	}else if(st == 2){
		return "Active (Bill Ready)";
	}else if(st == 3){
		return "Cancelled";
	}else if(st == 4){
		return "Suspended";
	}else if(st == 5){
		return "Expired";
	}else if(st == 10){
		return "Enabled";
	}else if(st == 11){
		return "Suspended";
	}
	return "N/A";
}

function getOS(num){
    if(num.startsWith("6.1")){
        return "Windows 2008 R2";
    }else if(num.startsWith("6.2")){
        return "Windows 2012";
    }else if(num.startsWith("6.3")){
        return "Windows 2012 R2";
    }else if(isNumeric(num) ){
        return "Windows ("+num+")";
    }else{
        return num;
    }
}
function prettyNet(s){
	var thresh = 1024;
    if(s < thresh) return s + ' bps';
    var units = ['KB/s','MB/s','GB/s'];
    var u = -1;
    do {
        s /= thresh;
        ++u;
    } while(s >= thresh);
    return s.toFixed(0)+' '+units[u];	
}
function prettySize(bytes) {
	if(bytes == null){ bytes =0;}
    var thresh = 1024;
    if(bytes < thresh) return bytes + ' Bytes';
    var units = ['kB','MB','GB','TB','PB','EB','ZB','YB'];
    var u = -1;
    do {
        bytes /= thresh;
        ++u;
    } while(bytes >= thresh);
    return bytes.toFixed(0)+' '+units[u];
}

function prettyThroughput(bytes, sec){
	const bytesInGB = 1024 * 1024 * 1024;
	const bytesInMB = 1024 * 1024;
	const bytesInTB = 1024 * 1024 * 1024 * 1024;
	let amt, unit;

	if (bytes >= bytesInTB) {
		amt = (bytes / sec)*60 / bytesInTB;
		unit = 'TB/min';
	} else if (bytes >= bytesInGB) {
		amt = (bytes / sec)*60 / bytesInGB;
		unit = 'GB/min';
	} else {
		amt = (bytes / sec)*60 / bytesInMB;
		unit = 'MB/min';
	}

	return `${amt.toFixed(2)} ${unit}`;
}

function printSize(bytes){
	var suf= " Bytes";
	var b = bytes;
	if(bytes < 1024){ return bytes+ suf; }	
	else if(bytes < (1024 * 1024) ){
		b= (bytes / (1024 ));
		suf =" KB";
	}else if(bytes < (1024 * 1024 * 1024)){
		b= (bytes / (1024 * 1024 ));
		suf =" MB";
	}else if(bytes < (1024 * 1024 * 1024 * 1024 )){
		b= (bytes / (1024 * 1024 * 1024 ));
		suf=" GB";
	}else if(bytes < (1024 * 1024 * 1024 * 1024 * 1024)){
		b= (bytes / (1024 * 1024 * 1024 * 1024)); 
		suf=" TB";
	}else if(bytes < (1024 * 1024 * 1024 * 1024 * 1024 * 1024)){
		b= (bytes / (1024 * 1024 * 1024 * 1024 * 1024)); 
		suf=" PB";
	}
	if(Math.round(b) != b){ b = b.toFixed(2); }

	return b + suf;
}

function prettyFloat(val) {
	const roundMe = Math.round(val * 100) / 100;
	if (Number.isInteger(roundMe)) {
		return roundMe.toFixed(0); 
	} else {
		return roundMe.toFixed(2); 
	}
}

function timeElapsed(s){
	s = Math.round(s);
    var ret="";
    var d = Math.floor(s/3600/24); //Get whole days
	if(d>7){
		return "Error";
	}else if(d>0){
		ret = d+" days";
		return ret;
	}
    var h = Math.floor(s/3600); //Get whole hours
    if(h>0){
        if(h==1){
            ret = h+" hour, ";
        }else{
            ret = h+" hours, ";
        }
    }
    s -= h*3600;
    var m = Math.floor(s/60); //Get remaining minutes
    if(m > 0){
        if(m > 1){
            ret += m+" mins ";
        }else{
            ret += m+" min ";
        }
    }
    s -= m*60;
    ret += s+" sec";
    return ret;
    //return h+":"+(m < 10 ? '0'+m : m)+":"+(s < 10 ? '0'+s : s); //zero padding on minutes and seconds
}

function validationTime(v){
    if(v==0){
        return getText('alwaysCheck');
    }else if(v == -1){
        return getText('neverCheck');
    }else if(v < 24){
        return v +" "+getText('hourUnits');
    }else if(v < 168){
        var ret = Math.round(v/24);
        if( ret ==1){ ret += " "+getText('dayUnit'); }
        else{ret += " "+getText('dayUnits'); }
        return  ret;
    }else if(v < 336){
        var days = Math.floor((v - 168) / 24);
        var ret= "1 "+getText('weekUnit');
         if(days > 0){ ret += ", "+ days +" "+getText('dayUnits'); }
         return ret;
    }else if(v <= 2160){
        var weeks = Math.floor(v /168);
        var days = Math.round((v - (weeks * 168)) / 24);
        var ret = weeks +" "+getText('weekUnits');
        if(days> 0){ ret += ", "+days+" "+getText('dayUnits'); }
        return  ret
    }else if(v > 2160 || v == -1){
        return getText('neverCheck');
    }
}

function isChecked(thing){
    if(isCheckedBool(thing)){ return "checked"; }
    return "";
}
function isCheckedBool(thing){
    if(thing ==1 || thing == "true" || thing == "1"){ return true; }
    return false;
}

function showHideNote(){
    if($("#notificationBox > div").length > 0){
        $("#notificationBox").removeClass("hidden");
    }else{
        $("#notificationBox").addClass("hidden");
    }
}
function doToast(text, titl, cls=""){

	let opts = {};
	opts.title = titl;
	opts.body = text;
	var classy="";
	var deelay = 2500;
	var auto = true;
	var icon = "fas fa-info-circle fa-lg";
	if(cls != ""){
		if(cls == "success"){
			opts.class = "bg-success";
		}else if(cls == "alert" || cls== "error"){
			opts.class = "bg-danger";
			auto = true;
			deelay = 12000;
		}else if(cls== "warning"){
			opts.class = "bg-warning";
			deelay = 5000;
		}
	}
	opts.autohide = auto;
	opts.icon = icon;
	opts.delay = deelay;
	opts.position = "bottomRight";
	
	$(document).Toasts('create', opts )
}

function addPopup(text){
    var id = (new Date).getTime();
    var note = "<div class=\"notifyItem\" id=\""+id+"\">"+text+" <span class=\"closeNote\"> (x) </span></div>";
    $("#notificationBox").append(note);
    showHideNote();
    setTimeout(function(){
        var fade = { opacity: 0, transition: 'opacity 0.5s' };
        var tag = "#"+id;
        $(tag).css(fade);
        $(tag).slideUp();
        
        setTimeout(function(){
            $(tag).remove();
            showHideNote();            
            }, 250);
    }, 7000);
}

function clearModalError(){
    $('#modal-error').text("");
    $('#modal-success').text("");
}

function testPathChars(path){
    if(path.match(/[\<\>!@#%^&\*\?|\":;]+/i) ) {
        return false;
    }
    return true;
}
function testValidDS(path){
    if((path.split("/").length <  4) && path.split("\\").length <  4){
        return false;
    }
}


function toggleSlashes(val, which){
    if(!val){ return ""; }
    if (which == "//"){
        val =val.replace(/\\/g,"/");        
    }else{
        val = val.replace(/\//g,"\\");
    }
    return val;
    
    if(val.indexOf("/") !== -1){
        val = val.replace(/\//g,"\\");
    }else{
        val =val.replace(/\\/g,"/");        
    }
    return val;
}



function loadTextStrings(lang){
    $.ajax({
      url: "/tooltips."+lang,
      dataType: 'json',
      async: false,
      
      success: function(data) {
        window.tips = data;
      }
    });

//    $.getJSON( "/tooltips."+lang, function( data ) {
//    })
//    .done(function(data){
//        if(data){
//            window.tips = data;
//        }
//    });
}
function getText(elem){
    return getTextString(elem, getLanguage())
}
function getTextString(elem, lang){
    if(window.tips){
        if(window.tips[elem]){
            return window.tips[elem];
        }else{
            //addPopup("DEBUG: Missing text for element: "+ elem+", lang: "+lang);
            return "Text could not be loaded!";
        }
    }
    loadTextStrings(lang);
    
    if(window.tips && window.tips[elem]){
        return window.tips[elem];
    }else{
        return "Text could not be loaded!";
    }
}

function printInput(name, label, placeholder, site, style,  value=null){
	var val = "";
	if(value != null){ val = "value='"+val+"'"; }
	var p = "<div class='row'><div class='col'><span class='none'>"+label+"</span></div><div class='col'><input id='"+name+"' name='"+name+"' type='text' class='form-control "+style+"' placeholder='"+placeholder+"' "+val+"></div></div>";
	return p;
}
function printToggle(name, text, checked, style=null){
	return printToggleSite(name, text, checked,  0, style );
}
function printToggleSite(name, text, checked, site, style=null){
	var ck = "";
	var cls = "";
	var special = "";
	if(checked == 1 || checked == true){ ck = "checked"; }	
	if(style != null){ 
		cls = style; 
		if(cls.includes('danger')){ special = cls; }	 // these have to go on the div, not the input
	}	
	var t = "<div class='custom-control custom-switch "+special+"'> <input type='checkbox' "+ck+" class='custom-control-input "+cls+"' id='"+name+"' data-site="+site+"> <label class='custom-control-label' for='"+name+"'>"+text+"</label> </div>";
	return t;
}

function printCheck1(name, text, checked){
    var c ="<input type='checkbox' name='"+name+"' id='"+name+"' ";
    if(checked == "true" || checked == true){ c+= " checked"; }
    c += "><label for='"+name+"'> "+text +"</label>";
    return c;
}
function printCheck2(name, checked, enabled=true){
    var c ="<span class='qsCheck'><input type='checkbox' name='"+name+"' id='"+name+"' ";
    if(enabled == false ){ c+= " disabled"; }
    else{
	    if(checked == "true" || checked == true){ c+= " checked"; }
    }
    c += "><label  for='"+name+"'></label> "+getText(name) +"</span>";
    return c;
}
function printCheck3(name, text, style, checked){
    var c ="<span class='qsCheck'><input type='checkbox' name='"+name+"' id='"+name+"' class='"+style+"' ";
    if(checked == "true" || checked == true){ c+= " checked"; }
    c += "><label  for='"+name+"'></label> "+text +"</span>";
    return c;
}
function printCheck(name, style, checked){
    var c ="<span class='qsCheck'><input type='checkbox' name='"+name+"' id='"+name+"' class='"+style+"' ";
    if(checked == true){ c+= " checked"; }
    c += "><label for='"+name+"'></label> "+getText(name) +"</span>";
    return c;
}

function printCheckReg(name, text, checked){
    var c ="<span class='qsCheck'><input type='checkbox' name='"+name+"' id='"+name+"'  ";
    if(checked == true){ c+= " checked"; }
    c += "><label for='"+name+"'></label> "+text +"</span>";
    return c;
}

function inArr(it, arr){
    if($.inArray(it, arr) > -1) {return true; }
    return false;
}
 function padInt(num){
    var s = num;
    if(num < 10){ s = "0"+num; }
    return s;
}

function fixJsonArgs(args){
    if(!args){ return; }
    $.each(args,function(k,val){ 
        if(isNumeric(val)){
            args[k] = val.toString();
            }
     });
}

function wsGet(call, callback, args){
	var url = getWsCall(call, 0);
	$.ajax({
                url: url,
                dataType: "json",
                type: 'get',
		timeout: (window.wsTimeout * 1000),
                async: false,
                success: function(response) {
			var result;
			try{
				result = response;
			}catch(err){
				$('#modal-error').text("Error:"+err.message);
				result.result = "error";
				result.message = err.message;
			}
			if(args){
				callback(result, args);
			}else{
				callback(result);
			}
                   },
                error: function(exception){
                        if(exception.statusText.includes("XMLHttpRequest")){
                                console.log(exception.statusText);
				doToast("Failed to connect to WebService.  Perhaps due to a networking failure or the WS is not running.", "WS Networking error", "error");
                        }else{
				doToast(JSON.stringify(exception.statusText), "Invalid WS Results", "error");
                        }
                   },
                done: function(){ }
        });
}

function wsCall(call, args, callback, param, site=0){
	var theUrl=getWsCall(call, site);
	fixJsonArgs(args);
	var data = {"data":JSON.stringify(args)};
        $.ajax({
                url: theUrl,
                dataType: "json",
                type: 'post',
		timeout: (window.wsTimeout * 1000),
                data: data,
                async: true,
	      success: function(results) {
			if(results.result == "success"){
				if(callback){
					if(param){ window[callback]( results, param); }
					else{ window[callback]( results); } 
				}
			} else{ 
				if(results.result == "error" && results.message.toLowerCase().includes("authentication required")){
					doToast("Session expired.  Please login again.", "Session Expired", "warning");
					setTimeout(function () { location.reload(); }, 2000);
				}else{
					if(callback){
						if(param){ window[callback]( results, param); }
						else{ window[callback]( results); } 
					}else{
						doToast("Received the error:"+results.message+"<br>Response:"+results, "Invalid WS Results", "error");
					}
				}
			}
	      },
	      error: function(xhr, status, error) {
			doToast("Received the error:"+error, "Invalid WS Results", "error");
	      }
	    });
}

function wsDownload(call, args, site=0) {
	var theUrl=getWsCall(call, site);
	fixJsonArgs(args);

        var tmp = new tempForm(theUrl, args);
        tmp.addParameter("data", JSON.stringify(args));
        tmp.submit();
	return;


	var data = {"data":JSON.stringify(args)};
        $.ajax({
                url: theUrl,
                method: 'post',
//		timeout: (window.wsTimeout * 1000),
                data: data,
		responseType: 'blob',
	      success: function (data, textStatus, xhr) {
			let filename = ''; // Extract filename from response headers if available
			let disposition = xhr.getResponseHeader('Content-Disposition');
			if (disposition && disposition.indexOf('attachment') !== -1) {
			    let filenameRegex = /filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/;
			    let matches = filenameRegex.exec(disposition);
			    if (matches !== null && matches[1]) {
				filename = matches[1].replace(/['"]/g, '');
			    }
			}
			    let blob = new Blob([data], { type: 'application/octet-stream' });
			    // Check if createObjectURL is available
			    if ('URL' in window) {
				let url = window.URL.createObjectURL(blob);
				let link = document.createElement('a');
				link.href = url;
				link.setAttribute('download', filename || 'file'); // Set the filename for the download
				document.body.appendChild(link);
				link.click();

				// Clean up
				document.body.removeChild(link);
				window.URL.revokeObjectURL(url);
			    } else {
				console.error('Browser does not support createObjectURL');
			    }

	      },
	      error: function(xhr, status, error) {
			doToast("Received the error:"+error, "Invalid WS Results", "error");
	      }
    });
}
///////////////////////// await/Promise versions
async function wsPromise(call, args, site) {
  return new Promise((resolve, reject) => {
        var theUrl=getWsCall(call, site);
        fixJsonArgs(args);
        var data = {"data":JSON.stringify(args)};
        $.ajax({
                url: theUrl,
                dataType: "json",
                type: 'post',
		timeout: (window.wsTimeout * 1000),
                data: data,
                async: true,

      success: function(results) {
		if(results.result == "success"){
			resolve(results);
		} else if(results.result == "error" && results.message.toLowerCase().includes("authentication required")){
			doToast("Session expired.  Please login again.", "Session Expired", "warning");
			setTimeout(function () { location.reload(); }, 2000);
		} else{ reject(results); }
      },
      error: function(xhr, status, error) {
        reject(error);
      }
    });
  });
}
///////////////////////// await/Promise versions

function getWsCall(call, site){
	 return  window.alikeSite+ '/ws/'+site +'/'+ call;// +"?session="+getLocalSession();
}
function getSessionForSite(site){
	if (site == 0){
		return getLocalSession();
	}
	var guy = JSON.parse(sessionStorage.getItem(site));
	return guy.session;
}
function getSite(site=0){
	if(site==0){ return window.alikeSite; }
	var guy = JSON.parse(sessionStorage.getItem(site));
	return guy.url;
}
function setSites(sites){
	var proto = "https://";
	if (window.location.protocol === 'http:') { proto = "http://"; }
	jQuery.each(sites, function(i,s){
		var guy = {};
		guy.url = proto+s.ip;
		guy.session = s.password;
		sessionStorage.setItem(s.id, JSON.stringify(guy));
	});
}



function tempForm(url){
    var object = this;
    object.time = new Date().getTime();
    object.form = $('<form action="'+url+'" target="iframe'+object.time+'" method="post" style="display:none;" id="form'+object.time+'"></form>');

    object.addParameter = function(parameter,value)    {
        $("<input type='hidden' />").attr("name", parameter).attr("value", value).appendTo(object.form);
    }
    object.submit = function() {
        var iframe = $('<iframe data-time="'+object.time+'" style="display:none;" id="iframe'+object.time+'"></iframe>');
        $( "body" ).append(iframe); 
        $( "body" ).append(object.form);
        object.form.submit();
        iframe.load(function(){  $('#form'+$(this).data('time')).remove();  $(this).remove();   });
    }    
    
}

function getAlikeVersion(bld){
    //var apos = bld.indexOf("Alike ");
    if(!bld){
        return "unknown";
    }
    var apos =0;
    var bpos = bld.indexOf("build ");
    var build = bld.substring(apos + 6,bpos -1);
    return build;
}

function colorPicker(hex, lvl) {
	//hex = String(hex).replace(/[^0-9a-f]/gi, '');	
	lvl = lvl || 0;
	// convert to decimal and change luminosity
	var rgb = "#", c, i;
	for (i = 0; i < 3; i++) {
		c = parseInt(hex.substr(i*2,2), 16);
		c = Math.round(Math.min(Math.max(0, c + (c * lvl)), 255)).toString(16);
		rgb += ("00"+c).substr(c.length);
	}
	return rgb;
}

function checkForUpdate(display){
    var url = getText('updateCheckUrl');
    $.get(url, function(response) {
	        try{
			res = jQuery.parseJSON(response);
 
	            if(res.version > window.alikeBuild ){
	                var txt = getText('updateAvl')+": A3 Build "+res.version+" is ready for download on the console.</a>";
                    addPopup(txt);
                    var args= new Object();
                    args["msg"] = txt;
                    args["level"] ="0";
                    args["newBuildNum"] =res.version;
                }else{
                    if(display==true){
                        var txt = "No new updates found. <br>Current build ("+window.alikeBuild+") is the most recent (stable) available.";
                        addPopup(txt);
                    }
                }
                
	        }catch(err){	        
	            addPopup("Failed to check for updates.  \n\nError: "+err.message +" \nResponse: "+response);
	        }	        
	   }).error(function(){
		  
	   }).done(function(){
	});
}

function startBKS(){
    addPopup(getText("start-service"));
    $("#serviceState").removeClass(); 
    $("#serviceState").addClass('service-state-pending');
    var args= new Object();
    args["state"] = "start";
    wsCallGz("serviceState",args,"","Failed to start Alike Services", null, null,true);
}
function stopBKS(){
	$.confirm({
	    text: getText('bks-stop'),
	    confirm: function() {
			addPopup(getText("stop-service"));
			$("#serviceState").removeClass(); 
			$("#serviceState").addClass('service-state-pending'); 
			var args= new Object();
			args["state"] = "stop";
			wsCallGz("serviceState",args,"","Failed to start Alike Services", null, null,true);
	    },
	    cancel: function() { }
	});
}

function getServiceState(){
    if($('#serviceState').hasClass('service-state-on') ){
        return "running";
    }else if($('#serviceState').hasClass('service-state-off')) {
        return "stopped";
    }else{
        //$('#serviceState').addClass('service-state-pending');
        return "pending";
    }
}

function jumpToAnchor(aid, elementID){
    var aTag = $("a[name='"+ aid +"']");
    $("#"+elementID).animate({scrollTop: aTag.offset().top},'slow');
}


function getUserFilter(){
    var filter =/^[A-Za-z0-9-_\\\./]+$/;
    return filter;
}

function vmInList(uuid, VMs){
    var found = false;
    jQuery.each(VMs, function(i,v){
        if(uuid == v.UUID){ 
            found = true;
            return false; 
        }
    });
    return found;
}

function generateMAC(){
	var maryMac= "00:16:3E:XX:XX:XX";
	maryMac = maryMac.replace(/X/g, function() { return "0123456789ABCDEF".charAt(Math.floor(Math.random() * 16)) });
	return maryMac;
}

function makeCRCTable(){
    var c;
    var crcTable = [];
    for(var n =0; n < 256; n++){
        c = n;
        for(var k =0; k < 8; k++){
            c = ((c&1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1));
        }
        crcTable[n] = c;
    }
    return crcTable;
}

function makeCRC(str) {
    var crcTable = window.crcTable || (window.crcTable = makeCRCTable());
    var crc = 0 ^ (-1);

    for (var i = 0; i < str.length; i++ ) {
        crc = (crc >>> 8) ^ crcTable[(crc ^ str.charCodeAt(i)) & 0xFF];
    }

    return (crc ^ (-1)) >>> 0;
};

function AlikeUIStale(msg){
    displayGenericModal(false);
    $("#welcomeBody").text('');
    $("#welcomeModalDialogContainer").width('500px');
    $("#welcomeModalDialogContainer").css("maxWidth",'100%');
    
    var p = "<center><br><span class='blueTitle'>"+getText("stale-cache-title")+"</span></center>";
    p += "<div class='centered padded'>";
    p += "<div class='spaced'>";
    p += "<span class='greySmall'> "+getText('stale-cache-note');
	if(msg){ p += msg; }
    p += "</span><br><hr>";    
    p += "</div></div>";
    $("#welcomeBody").html(p);    
    displayWelcomeModal(true);
}


function submitExternal(url, params) {
    var f = $("<form target='_blank' method='POST' style='display:none;'></form>").attr({
        action: url
    }).appendTo(document.body);

    for (var i in params) {
        if (params.hasOwnProperty(i)) {
            $('<input type="hidden" />').attr({
                name: i,
                value: params[i]
            }).appendTo(f);
        }
    }
    f.submit();
    f.remove();
}

function loadScript(url, callback) {
    var head = document.getElementsByTagName('head')[0];
    var script = document.createElement('script');
    script.type = 'text/javascript';
    script.src = url;
    script.onreadystatechange = callback;
    script.onload = callback;
    head.appendChild(script);
}

function getProto(){
	if( document.location.protocol == 'https:' ){
		return 'https';
	} else{
		return 'http';
	}
}

function clamp(num, min, max) {
	return num <= min ? min : num >= max ? max : num 
}

function downloadFile(url, fileName) {
	var xhr = new XMLHttpRequest();
	xhr.open('GET', url, true);
	xhr.responseType = 'blob';

	xhr.onload = function () {
	if (xhr.status === 200) {
	var blob = xhr.response;
	var link = document.createElement('a');
	link.href = window.URL.createObjectURL(blob);
	link.download = fileName;
	link.click();
	}
	};

	xhr.onprogress = function (event) {
		var percentComplete = (event.loaded / event.total) * 100;
		console.log('Download progress: ' + percentComplete.toFixed(2) + '%');
	};

	xhr.send();
}

function pruneStorage(max) {
	if (sessionStorage.length <= max) { return; }	// we're good

	const deadMen = [];
	for (let i = 0; i < sessionStorage.length - max; i++) {
		deadMen.push(sessionStorage.key(i));
	}
	for (const key of deadMen) { sessionStorage.removeItem(key); }
}

function displayLogin(showit){
        alert("DoLogin");
}

function showCrumbs(){
	var c = "History: ";
	if(window.crumbs == null){
		c += "None";
	}else{
		window.crumbs.forEach(function(it){
			c += it +" /";
		});
		if (c.endsWith('/')) { c = c.slice(0, -1); }
	}
	return c;
}

function addCrumb(it){
	const index = window.crumbs.indexOf(it);
	if (index !== -1) {
		window.crumbs.splice(index, 1);
	}
	window.crumbs.push(it);
	if (window.crumbs.length > 3) {
		window.crumbs.shift();
	}
}

function getStateClass(st){
	var cls = "";
	if(st ==0){
		cls = "badge badge-success";
	}else if(st == 1){
		cls = "badge badge-danger";
	}else if(st == 2){
		cls = "badge badge-warning";
	}else if(st == 3) {
		cls = "badge badge-primary";
	}else if(st == 4) {
		cls = "badge bg-gray disabled";
	}
	return cls;	
}

function makePB(perc, color){
        if(typeof perc == "string"){
                perc = perc.trim();
        }
        if(color === undefined){
                if(perc < 25){ color = "bg-success"; }
                else if(perc < 50){ color = "bg-info"; }
                else if(perc < 85){ color = "bg-warning"; }
                else { color = "bg-danger"; }
        }
        var pb;
        if(perc > 35){
                pb = "<div class='progress'><div class='progress-bar "+color+"' role='progressbar' style='width: "+perc+"%' aria-valuenow='"+perc+"' aria-valuemin='0' aria-valuemax='100'><span class='text-sm'>"+perc+"%</span></div> </div>";
        }else{
                pb = "<div class='progress'><div class='progress-bar "+color+"' role='progressbar' style='width: "+perc+"%;' aria-valuenow='"+perc+"' aria-valuemin='0' aria-valuemax='100'></div> &nbsp;<span class='text-sm'>"+perc+"%</span> </div>";
        }

        pb = "<div class='progress rounded'><div class='progress-bar "+color+"' role='progressbar' aria-valuenow='"+perc+"' aria-valuemin='0' aria-valuemax='100' style='width: "+perc+"%'> <span class='text-center'>"+perc+"%</span> </div> </div>";
	if (perc < 10){
		pb = "<div class='progress rounded'><div class='progress-bar "+color+"' role='progressbar' aria-valuenow='"+perc+"' aria-valuemin='0' aria-valuemax='100' style='width: "+perc+"%'></div> &nbsp; <span class='text-center'></span> </div>";
	}

        return pb;

}

function printTip(tip){
	var t = " &nbsp; <i class='fas fa-question-circle' title='"+tip+"' data-toggle='tooltip' data-html='true'></i> &nbsp; ";
	return t;
}
function renderTips(){
	$(function () { $('[data-toggle="tooltip"]').tooltip() })
}

function maskToCIDR(net) {
	return (net.split('.').map(Number).reduce((a, b) => (a << 8) + b) >>> 0).toString(2).split('1').length - 1;
}
function CIDRToMask(cidr) {
	const binStr = '1'.repeat(cidr) + '0'.repeat(32 - cidr);
	return binStr.match(/.{8}/g).map(b => parseInt(b, 2)).join('.');
}

function checkIP(ip){
        var ipPattern = /^(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$/;
        if (!ipPattern.test(ip)) { return false; }
	return true;
}

function finishLineLoad(){
        ///$("#loadingLine").css("animation", "expandLine 0.5s forwards");
	$('#loadingLine').css('animation-play-state', 'paused');
        $("#loadingLine").removeClass("loadingLine");
        $("#loadingLine").css("animation", "");
        var screenWidth = $(window).width();
//        $("#loadingLine").css("width", screenWidth + "px");
	setTimeout(function() { $('#loadingLine').css('display', 'none'); }, 200);
}
function showLineLoad(){
        $("#loadingLine").css("display", "block");
        var screenWidth = $(window).width();
        $("#loadingLine").css("width", screenWidth + "px");
        $("#loadingLine").addClass("loadingLine");
        $("#loadingLine").css("animation", "expandLine 4s forwards");
	
}
function showMiniPie(element, free){
	//var uc = "#007fff";	// blue
	//var fc = "#ffe900";	// yellow
	free = Math.round(free);
	var uc = "#ffe900";	// yellow
	var fc = "#00a65a";	// green

	if(free < 5){ fc = "#f56954"; }
	else if(free < 25){ fc = "#F3C30F"; }
	else if(free < 50){ fc = "#00c0ef"; }

	$("#"+element).sparkline([100 - free,free], { type: 'pie', sliceColors: [uc,fc], borderWidth: 0 });
}
function showKnob(id, perc,lbl, style=null){
	var cls = "#00c0ef";
	if(perc > 95){ cls = "#f56954"; }
	else if(perc > 75){ cls = "#F3C30F"; }
	else if(perc > 50){ cls = "#00c0ef"; }
	else { cls = "#00a65a"; }

	if(style == "green"){ cls= "#00a65a"; }
	else if(style == "yellow"){ cls= "#F3C30F"; }
	else if(style == "red"){ cls= "#f56954"; }
	var out='<input id='+id+' type="text" class="knob" value="'+perc+'" data-width="90" data-height="90" data-fgColor="'+cls+'">'
	out += '<div class="knob-label">'+lbl+'</div>';
	return out;
}

function printPanel(title, body, cls=""){
	// cls could be 'card-primary'
	var p = `<div class="card ${cls}"><div class="card-header">
		<h3 class="card-title" style="width: 100%;"> ${title} </h3>
		</div><div class="card-body"> ${body} </div></div>`;
	return p;
}

function printPanelEx(title, body, cls, butCls="<i class='fas fa-sync-alt'></i>"){
	
	// cls could be 'card-primary'
	var p = `<div class="card ${cls}"><div class="card-header">
		<h3 class="card-title" > ${title} </h3>
		<div class='card-tools'>
			<button type="button" class='btn btn-tool' >
				${butCls}
			</button>
		</div>
		</div><div class="card-body"> ${body} </div></div>`;
	return p;
}
function drawDiskPie(total, free, element, aspect=false){
        var size = 1024 * 1024;
        var unit = "MB";
        if(total > 1024 * 1024 * 1024 * 1024){ size  *= (1024 * 1024);  unit = "TB"; }
        else if(total > 1024 * 1024 * 1024){ size  *= 1024;  unit = "GB"; }
        var a = Number(((total- free) / size).toFixed(1));
        var f = Number((free/ size).toFixed(1));
        var perc = (free / total) * 100;

        var freeColor = "#26a300";
        var usedColor = "#0075d6";
        if(perc < 2 ){
                freeColor = "#e24141";
                usedColor = "#e24141";
        }else if(perc < 15 ){
                freeColor = "#e24141";
                usedColor = "#f29730";
        }else if (perc < 20){
                freeColor = "#f49b42";
                usedColor = "#e0e847";
        }
	if($("#"+element).prop('disabled')){
                freeColor = "#CDD3D6";
                usedColor = "#8A9398";
	}
        var piedata = {
                datasets: [{ data: [a,f], backgroundColor: [ usedColor, freeColor  ] }],
                labels: [ "Storage Used", "Free Space" ],
        };


        var options = {
		plugins:{
		legend: { display: false },
                tooltip: {
                        mode: 'index',
                        intersect: false,
                        callbacks: {
                            label: function(context) {
				let label = " "+Math.abs(context.parsed) || '';
				if (context.parsed !== null) {
					label += " "+unit;
				}
				return label;
                            }
                        }
                }
		},
	
        responsive: true,
        maintainAspectRatio: aspect,
        segmentShowStroke : false,
        segmentStrokeColor : "#000",
        borderWidth : 0,
        segmentStrokeWidth : 1,
        cutoutPercentage : 50, // This is 0 for Pie charts
        animationSteps : 20,
        animationEasing : "easeOutQuart",
    };
        var ctx = document.getElementById(element);
        ctx = new Chart(ctx, { type:'doughnut', data: piedata, options: options });
}


function drawDisk3Pie(total, free, used, element, aspect=false){
        var size = 1024 * 1024;
        var unit = "MB";
        var usedComb = total - free;
        var usedOther = usedComb - used;

        if(total > 1024 * 1024 * 1024){ size  *= 1024;  unit = "GB"; }
        var a = Number((usedOther / size).toFixed(1));  // used (other)
        var f = Number((free/ size).toFixed(1));                // free
        var u = Number((( used ) / size).toFixed(1));   // used (sr)

        var perc = (free / total) * 100;

        var freeColor = "#26a300";
        var otherColor = "#fcba03";
        var usedColor = "#0075d6";
        if(perc < 1 ){
                freeColor = "#e24141";
                otherColor = "#fcba03";
                usedColor = "#e24141";
        }else if(perc < 10 ){
                freeColor = "#e24141";
                otherColor = "#fcba03";
                usedColor = "#f29730";
        }else if (perc < 25){
                freeColor = "#f49b42";
                otherColor = "#fcba03";
                usedColor = "#e0e847";
        }

	var colors = { free: freeColor, used: usedColor, other: otherColor }
        var piedata = {
                datasets: [{ data: [a,f,u], backgroundColor: [ usedColor, freeColor, otherColor  ] }],
                labels: [ "System Used", "Free Space", "SR Used" ],
        };
        var options = {
                plugins:{
                legend: { display: false },
                tooltip: {
                        mode: 'index',
                        intersect: false,
                        callbacks: {
                            label: function(context) {
                                let label = " "+context.parsed || '';
                                if (context.parsed !== null) {
                                        label += " "+unit;
                                }
                                return label;
                            }
                        }
                }
                },
        responsive: true,
        maintainAspectRatio: aspect,
        segmentShowStroke : false,
        segmentStrokeColor : "#000",
        segmentStrokeWidth : 1,
        borderWidth : 0,
        cutoutPercentage : 50, // This is 0 for Pie charts
        animationSteps : 20,
        animationEasing : "easeOutQuart",
    };
        if(!window.graphs){ window.graphs = []; }
	// this is needed to update dynamically
        if(window.graphs[element]){ window.graphs[element].destroy(); }
        var ctx = document.getElementById(element);
        new Chart(ctx, { type:'doughnut', data: piedata, options: options });

	return colors;
}


function updateSetting(set, val, site){
	var args = new Object();
	args["setting"]= set;
	args["value"]= val;
	wsCall("setting/update", args, "drawVmDetailHeader",null, site );
}


///////////////////////////
async function buildMetaCaches(){

	// TODO: add a ws call to get nodeMode for manager (to show/hide multi node features), and licensing/edition details

	wsCall("hostPools",null,"setPoolCacheCB",0 );	// start the slowest first

	let a3s = {}
	try{
                var res = await wsPromise("a3s", null, 0)
		a3s= res.A3s;
                sessionStorage.setItem("a3Cache", JSON.stringify(res.A3s) );
        }catch (ex){
                doToast(ex.message, "Failed to get A3 info", "warning");
        }
	for(const a of a3s){
		let site = a.id;
		wsCall("srs",null, "setSrCacheCB",site, site);
//		wsCall("templates",null, "setTemplateCacheCB",site, site);
		wsCall("networks",null, "setNetworkCacheCB",site, site);
	}
}
function setNetworkCacheCB(data, site){
        if(data.result == "success"){
                let cache = sessionStorage.getItem("networkCache") ;
                if(cache == null){ 
                        console.log("Creating Network cache");
			var netAssoc = {};
			$.each(data.networks, function(index, n) {
				if (!netAssoc[n.poolid]) { netAssoc[n.poolid] = []; }
				netAssoc[n.poolid].push(n);
			});
                        sessionStorage.setItem("networkCache", JSON.stringify(netAssoc) );
                }
                else{
                        var netAssoc = JSON.parse(cache);
			$.each(data.networks, function(index, n) {
				if (!netAssoc[n.poolid]) { netAssoc[n.poolid] = []; }
			});
                        $.each(data.networks, function(i, s){
				let hasGuy = netAssoc[s.poolid].some(function(g) { return s.uuid === g.uuid; }); 
				if (hasGuy){ ; }
                                else{ 
                                        console.log("Adding "+i+" to Network cache");
                                        netAssoc[s.poolid].push(s);
                                }
                        });
                        sessionStorage.setItem("networkCache", JSON.stringify(netAssoc) );
                }
        } else{ doToast(data.message, "Failed to get Network info", "warning"); }
}

function setSrCacheCB(data, site){
	if(data.result == "success"){ 
		let cache = sessionStorage.getItem("srCache") ; 
		if(cache == null){ 
			console.log("Creating SR cache");
			sessionStorage.setItem("srCache", JSON.stringify(data.srs) ); 
		}
		else{
			cache = JSON.parse(cache);
			$.each(data.srs, function(i, s){
				if(i in cache ){ ;}
				else{ 
					console.log("Adding "+i+" to SR cache");
					cache[i] = s; 
				}
			});
			sessionStorage.setItem("srCache", JSON.stringify(cache) );
		}
	} else{ doToast(ex.message, "Failed to get SR info", "warning"); }
}
//function setTemplateCacheCB(data, site){
//        if(data.result == "success"){
//                let cache = sessionStorage.getItem("templateCache") ;
//                if(cache == null){ 
//			console.log("Creating Template cache");
//                        sessionStorage.setItem("templateCache", JSON.stringify(data.templates) ); 
//                }
//                else{
//                        cache = JSON.parse(cache);
//                        $.each(data.templates, function(i, s){
//                                if(i in cache ){ ;}
//                                else{ 
//					console.log("Adding "+i+" to Template cache");
//					cache[i] = s; 
//				}
//                        });
//                        sessionStorage.setItem("templateCache", JSON.stringify(cache) );
//                }
//        } else{ doToast(data.message, "Failed to get Template info", "warning"); }
//}
function setPoolCacheCB(data){
        var a3s={}

        $.each(data.A3s, function(i,a3){
                a3s[a3.id]= {};
                a3s[a3.id].pools = {};
                $.each(a3.pools, function(p, v){
                        var guy = { name: v, uuid: p, type: 0, srs: {}, hosts: {} }
                        a3s[a3.id].pools[p] = guy;
                });

                // add hosts to their pool.  if they are hv, make a pool.  
                $.each(a3.hosts, function(i,h){
			if(h.type ==10){ return; }
                        var guy = { name: h.name, uuid: h.guid, type: h.type, srs:{} }
                        if(h.poolid in a3s[a3.id].pools){
                                a3s[a3.id].pools[h.poolid].hosts[h.guid] = guy; // add to existing pool
                        }else{
                                a3s[a3.id].pools[h.poolid] = guy;               // call this guy it's own pool (hv or xen standalone)
                        }
                });
        });
        sessionStorage.setItem("poolCache", JSON.stringify(a3s) );
}

function clearMetaCaches(){
	sessionStorage.removeItem("a3Cache");	
	sessionStorage.removeItem("srCache");	
	sessionStorage.removeItem("poolCache");	
//	sessionStorage.removeItem("templateCache");	
}
///////////////// 
function getNameFromCache(guid){
	let cache = JSON.parse(sessionStorage.getItem("poolCache")) ;
	let name = "Unknown Guid";
	$.each(cache, function(i, pool){
		$.each(pool.pools, function(x, p){
			if(guid == p.uuid){ 
				name = p.name; 
				return false;
			}
			$.each(p.hosts, function(x, h){
				if(guid == h.uuid){ 
					name = h.name; 
					return false;
				}
			});
		});
	});
	return name;
}
function getA3FromGuid(guid){
	let cache = JSON.parse(sessionStorage.getItem("a3Cache")) ;
	let winner = null;
	$.each(cache, function(i, a3){
		if(a3.guid == guid){ 
			winner = a3;
			return false;	
		}
	});
	return winner;
}
function getA3FromId(id){
        let cache = JSON.parse(sessionStorage.getItem("a3Cache")) ;
        let winner = null;
        $.each(cache, function(i, a3){
                if(a3.id == id){
                        winner = a3;
                        return false;
                }
        });
        return winner;
}



function getGuidCache(){
	let cache = sessionStorage.getItem("guidCache");
        if(cache === null || cache.trim() == ''){
                return {}
        }
	return JSON.parse(cache);
}
function setGuidCache(cache){
	sessionStorage.setItem("guidCache", JSON.stringify(cache) );
}
function getGuidCacheItem(item){
        var cache = sessionStorage.getItem("guidCache");
        if(cache === null || cache.trim() == ''){ return null; }
	cache = JSON.parse(cache);
	if(item in cache ){ return cache[item]; }
	return null;
}
function setGuidCacheItem(guid, obj){
	let cache = sessionStorage.getItem("guidCache");
        if(cache === null || cache.trim() == ''){
                cache = {}
        }else{
		cache = JSON.parse(cache);
	}
	cache[guid] = obj;
	sessionStorage.setItem("guidCache", JSON.stringify(cache) );
}
///////////////////////////

function prepModal(title){
	$("#modalBody").text('');
	$(".modal-title").text(title);
        $("#modal-ok-button").html('Ok');
        $("#modal-ok-button").removeClass();
        $("#modal-ok-button").addClass('btn btn-primary');
        $("#modal-ok-button").prop('disabled', false);
	
}

function displayModal(showit){
   if(showit==true){
        $('#modal-main').modal('show');
    }else{
        $('#modal-main').modal('hide');
    }
}


function toObject(inGuy) {
	var ob = {};
	for (var i = 0; i < inGuy.length; ++i){ 
		ob[i] = inGuy[i]; 
	}
	return ob;
}

function isManager(){
	if(window.nodeMode ==0){
		return true;
	}
	return false;
}

function copyToClip(guid) {
	var tempInput = $("<input>");
	tempInput.val(guid);
	$("body").append(tempInput);
	tempInput.select();
	document.execCommand("copy");
	tempInput.remove();
}
