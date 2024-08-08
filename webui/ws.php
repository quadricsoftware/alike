<?php
include_once("/usr/local/sbin/common_lib");
include_once("/usr/local/sbin/manager_lib");
include_once("/usr/local/sbin/ws_lib");

// This must be handled before session creation to avoid deadlocking the browser
if (isset($_REQUEST["session"]) && isset($_REQUEST["file"]) && isset($_REQUEST["siteid"])){

	// check our session against the official QS WS session token
	$sess = $_REQUEST["session"];
	if ($sess != trim(file_get_contents("/tmp/qs.sess")) ){
		return returnError("Authentication Required!");
	}
	return downloadFlrFileMgr($_REQUEST);
}

$dir = dirname($_SERVER['PHP_SELF']);
$thePage = $_SERVER['PHP_SELF'];
$appBase =  $_SERVER['DOCUMENT_ROOT'] . '/';
$thePage = ltrim($thePage,"/");

if(isset($_REQUEST['req'])){ 
	if($_REQUEST['req'] == "api"){
		$apiReal = getSettingMgr("apiKey");
		$apiReal = empty($apiReal) ? 'API' : $apiReal;
		$apiKey = $_SERVER['HTTP_AUTHORIZATION'] ?? '';
		if($apiKey == ''){ 
			echo json_encode(["message" => "No api key provided"]);
			exit();
		}
		if ($apiKey !== $apiReal) {
			http_response_code(401);
			echo json_encode(["message" => "Unauthorized"]);
			exit();
		}

		array_shift($_REQUEST);
		$keys = array_keys($_REQUEST);
		if(count($keys) ==0){
			echo json_encode(["message" => "No method given"]);
			exit();
		}
		$call = $keys[0];
		array_shift($_REQUEST);
		$res = processClientApi($call, $_REQUEST);
		echo json_encode($res);

		exit();
	}
}

session_start();



$isLoggedIn=false;
$userid=0;


if(!isset($_REQUEST['req'])){ returnError("No site provided"); }
$site = $_REQUEST['req'];
array_shift($_REQUEST);

// now we just have the keys
$keys = array_keys($_REQUEST);
if(count($keys) ==0){
	returnError("Site: $site.  No method provided");
	exit();
}
$req = $keys[0];
$verb = null;
if(count($keys) >1){
	$verb = $keys[1];
}
//echo "Site: $site, Req: $req, Verb: $verb";

if($req == "logout"){
	session_destroy();
	return;
}

// for the licensing server/proxy code
if($site == "subproxy"){
	$verb = array_search("",$_REQUEST);

	if($verb == "update_check"){
		$dat = $_REQUEST;
		proxyUpdateCheck($dat);
	}else{
		$dat = file_get_contents('php://input');
		if($verb == "licenses"){
			$dat = urldecode($dat);
			proxyLicensing($dat);
		}else if($verb == "email"){
			proxyEmail($dat);
		}else if($verb == "usage"){
			$dat = urldecode($dat);
			proxySubUsage($dat);
		}else if($verb == "settings"){
			$dat = urldecode($dat);
			proxyA3Settings($dat);
		}
	}
	exit();
}

// The A3 nodes need to call back for licensing requests
if(isset($_REQUEST["client-token"])){
	$d = $_REQUEST["data"];
	$them = getA3ByGuid($d["a3id"]);
	$token = getA3Token($them);
	if ($token != $_REQUEST["client-token"] ){
		return returnError("Unauthorized client access."  );
	}

	if ($site == "licenseCheck"){
		$r = licenseCheckMgr((object)$d);
		return returnJSN($r);
	} else if ($site == "subInfo"){
		$r = array();
		$r["result"] = "success";
		$r["subInfo"] = getSubInfoMgr((object)$d);
		return returnJSN($r);
	} else if ($site == "agentRegister"){
		$r =registerAgent((object)$d, $them);
		return returnJSN($r);
	}
	return returnError("Unknown client request $site");
}

if((!isset($_SESSION["session_token"]) || $_SESSION['session_token'] == null) && $req != "auth" && $req != "licenses"){
	return returnError("Authentication Required? $req");
}
if($req != "auth" && $req != "licenses"){
//	if($_REQUEST["session"]!= session_id()){ 
//		return returnError("Authentication Required (wrong session)"); 
//	}
}


$test = "";
try{
	$test = checkMgrDBs();
	if($test != "ok"){
		$r = array();
		$r["result"] = "error";
		$r["noDBs"] =1;
		$r["message"] = $test;
		return returnJSN($r);
	}
}catch(Exception $ex){
	$r = array();
	$r["result"] = "error";
	$r["noDBs"] =1;
	$r["message"] = $ex->getMessage();
	return returnJSN($r);
}

$j=null;
if(isset($_REQUEST['data'])){
	$j = json_decode($_REQUEST['data']);
}

//$verb = array_search("",$_REQUEST);


try{
	$site = intval($site);
	if($site >0){
		return proccessNimbusRequest($site, $req, $verb, $j);
	}else{
		return proccessRequest($req, $verb, $j);
	}
}catch(Exception $ex){
	$msg = "Webservice encountered an error: ". $ex->getMessage();
	$msg .= $ex->getTraceAsString();
	exec("echo '$msg' >> /tmp/barf.txt");
	return returnError($msg);
}

#############################################################################################
####		Its all functions from here on down
#############################################################################################
function proccessNimbusRequest($site, $req, $verb, $j){
	return processNimbusRequest($req, $verb, $j );

	// the stuff below is just junk from the multi-node universe
	$a3 = getA3($site);
	if($a3->ip == "127.0.0.1"){
		return processNimbusRequest($req, $verb, $j );
	}

	ob_start();

	if(!is_object($a3) ){
		return returnError("Failed to find A3 with ID: $site");
	}
	$url = "http://". $a3->ip."/ws/$req/";
	if(!empty($verb)){ $url .= "$verb/"; }

	// do special thing for file downloads
	if($req == "logs"){
		header("Content-Type: application/octet-stream");
		header("Content-Transfer-Encoding: Binary");
//		header('Content-Encoding: gzip');
		header("Content-disposition: attachment; filename=\"$verb.log\"");
		$token = getA3Token($a3);
		echo getRemoteFile($url, $token);
		exit();
	}else if($req == "flrDownload"){
		downloadFlrFile( $j);
		exit();
	}

	$needSync = checkShouldSync($site, $req, $verb);
	$call = $req;
	if(!empty($verb)){
		$call .= "/$verb";
	}
	if($j== null){ 
		echo  json_encode(getRemoteWS("$req/$verb", $a3) );
	}else{
		echo json_encode(postRemoteWS("$req/$verb", $a3, json_encode($j) ));
	}
	ob_flush();

	sleep(1);
	if ($needSync == "schedule"){
		syncScheds($a3);
	}else if ($needSync == "job"){
		syncJobs($a3);
	}else if ($needSync == "vm"){
		syncVms($a3);
	}
	exit();
}


function proccessRequest($req, $verb, $j){
	switch($req){
		case "agent":
                        return handleAgent($j,$verb);
                        break;
		case "jobgraph":
                        return getJobGraphMgr();
                        break;
		case "dashData":
			return getDashData($j, $verb);
			break;
//		case "a3":
//			return handleA3($j, $verb);
//			break;
		case "a3s":
			return listA3s();
			break;
		case "licenses":
			return listLicenses();
			break;
		case "site":
			return handleSite($j, $verb);
			break;
		case "hostPools":
			return listHostPools($j);
			break;
		case "status":
			return doStatusMgr();
			break;
		case "notifications":
			return getNotifications();
			break;
		case "abds":
			return listABDsMgr($j);
			break;
		case "joblog":
			return listJobDetailsMgr($j);
			break;
		case "jobs":
			return listJobsMgr($j);
			break;
		case "schedules":
			return listSchedulesMgr($j);
			break;
		case "hosts":
			return listHostsMgr($j);
			break;
		case "versions":
			return listVersionsMgr($j);
			break;
		case "vms-tag":
			return listVMsByTagMgr($j);
			break;
		case "vms":
			return listVMsMgr($j);
			break;
		case "agents":
			return listAgentsMgr($j);
			break;
		case "backups":
			return listBackupsMgr($j);
			break;
		case "settings":
			return getSettingsMgr($j);
			break;
		case "authProfiles":
			return listAuthProfiles($j);
			break;
		case "gfsProfiles":
			return listGFSProfilesMgr($j);
			break;
		case "disks":
			return listDisksMgr($j);
			break;
		case "templates":
			return listXenTemplatesMgr($j);
			break;
//////////////////////////////////// All of these guys have 'sub' commands
		case "job":
			return handleJobMgr($j, $verb);
			break;
		case "host":
			return handleHostMgr($j, $verb);
			break;
		case "schedule":
			return handleScheduleMgr($j, $verb);
			break;
		case "vm":
			return handleVMMgr($j, $verb);
			break;
		case "version":
			return handleVersionMgr($j, $verb);
			break;
		case "gfsProfile":
			return handleGFSProfileMgr($j, $verb);
			break;
		case "vaulting":
			return handleVaultingMgr($j, $verb);
			break;
		case "abd":
			return handleABDMgr($j, $verb);
			break;
		case "abdnet":
			return handleABDNetMgr($j, $verb);
			break;
		case "support":
			return handleSupport($j, $verb);
			break;
		case "logsummary":
			return handleLogSummary($j, $verb);
			break;
		case "logs":
			return handleLogsMgr($j, $verb);
			break;
//////////////////////////////////////////////

		case "setting":
			return updateSettingMgr($j, $verb);
			break;

		case "testemail":
			$res = sendTestEmail();
			if($res->result == "error"){
				return returnResult(0, "Error sending mail: $res->message");
			}else{
				return returnResult(1, "Test email has been sent.");
			}
			break;
		case "autotune":
			return runCmdMgr("autotune",null, null);
			break;
		case "viewlog":
			return getLogMgr($j);
			break;
		case "testAgentVM":
			returnError( "Please don't call the Manager for this: $req");
			return testAgentVMMgr($j);
			break;
		case "testAgent":
			returnError( "Please don't call the Manager for this: $req");
			return testAgentMgr($j);
			break;
		case "listInstaboots":
			// MAX CAN REGEX SEARCH HERE
			//syslog(LOG_INFO, "Hello and welcome ");
			return listInstabootsMgr();
			break;
		case "instaboot":
			return handleInstaboot($j);
			break;
		case "xenPoolHosts":
			return xenPoolHostsMgr();
			break;
		case "subsync":
			return doSyncSubMgr();
			break;
		case "subupdate":
			return doSubUpdateMgr();
			break;
		case "promoSeen":
			return doPromoSeen();
			break;
		case "alerts":
			return handleAlertsMgr($j, $verb);
			break;
		case "ribbon":
			return listRibbon($j, $verb);
			break;
/////////////////////////////////////////////////////////////
		case "browseRestoreFS":
			returnError( "REMOVE THIS WS! : $req");
			return doBrowseFLRMgr($j);
			break;
/////////////////////////////////////////////////////////////
	}
	returnError( "No such method (Mrg): $req");
}


function testAgentMgr($d){
	$r = array();
	if(!isset($d->virtType)){ $d->virtType =10; }
	$res = "success";
	$message ="Test passed successfully";
	$code=0;
	$output;
	if($d->virtType ==2){
		if(empty($d->ip)){
			$res = "error";
			$message = "Invalid Xen IP:  $d->ip";
		}else{
			$out = testXenAuth($d->ip, $d->username, $d->password);
			if($out ===true){
				$res = "success";
				$message = "Successfully connected to Xen host";
			}else{
				$res = "error";
				$message = "Failed to connect to Xen host: $out";
			}
		}
	}else{

		if(empty($d->ip)){
			$res = "error";
			$message = "Agent, invalid IP:  $d->ip";
		}else{
			$out = agentStatus($d->ip);
			if(empty($out) || !is_object($out)){
				$res = "error";
				$message = "Failed to connect to remote agent ($d->ip) <br>Please check the Agent is installed and running.";
			}else{
				$message = "Successfully connected to agent ($d->ip), status:".$out->version;
			}
		}
	}
        $r["result"] = $res;
        $r["message"] = $message;
        return returnJSN($r);
}
function testAgentVMMgr($d){

        $r = array();
        $res = "error";
        $message ="Test not started";
        $code=0;
        $output;
	$vm = null;
	if(!empty($d->uuid)){
		$vm = getVM($d->uuid);
	}else if(!empty($d->vmid)){
		$uuid = getVmUuid($d->vmid);
		$vm = getVM($uuid);
	}

	if(empty($vm)){
		$res = "error";
		$message = "Could not find VM";
	}else if(empty($vm->ip)){
		$res = "error";
		$message = "Could not find VM IP";
	}else{
		$out = agentStatus($vm->ip);
		if(empty($out) || !is_object($out)){
			$res = "error";
			$message = "Failed to connect to remote agent ($vm->ip) <br>Please check the Agent is installed and running.";
		}else{
			$res = "success";
			$ver = explode(' ', $out->version);
			$message = "Detected agent version: &nbsp; ".$ver[0];
			$out = agentParseWmi(agentWmiCmd($vm->ip, "SELECT name FROM Win32_OperatingSystem"));
			if(!isset($out->Name) || empty($out->Name)){
				$res = "error";
				$message = "Connected to QHB agent (". $ver[0].")<br>&nbsp; but Windows is denied remote WMI ";
			}else{
				$message .= "<br> WMI OS:   $out->Name";
			}
		}
	}

        $r["result"] = "success";;
        $r["thing"] = $res;
        $r["message"] = $message;
        return returnJSN($r);
}
function doSubUpdateMgr(){
	$r = array();
	$r["result"] = "success";
	$r["message"] = "Sub info updated";
	$inf = getSubInfoMrg();
	$r["sockets"] = $inf->sockets;
	$r["edition"] = $inf->edition;
	$r["type"] = $inf->type;
	$r["a3Name"] = $inf->a3Name;
        returnJSN($r);

}
function doSyncSubMgr(){
	$r = array();
	$res = null;
	try{
		$res = syncSub();
		$r["result"] = "success";
		$r["message"] = "Sub synched: ".print_r($res, true);
	}catch(Exception $ex){
		$r["result"] = "error";
		$r["message"] = "Failed to get sub details: ". $ex->getMessage();
		$r["extended"] = print_r($res, true);
	}
        returnJSN($r);
}


function getLogMgr($d){
	$f = "/home/alike/logs/a3.log";
	if($d->log == "Data"){ $f = "/home/alike/logs/engine.log"; }
//	else if($d->log == "Syslog"){ $f = "/home/alike/logs/messages"; }

	$log = array();
        if(file_exists($f)){
                $file = explode("\n",shell_exec("tail -300 $f"));
                $max = 300;
                for ($i = max(0, count($file)-$max); $i < count($file); $i++) {
                        array_push($log, $file[$i]);
                }
        }


	$r = array();
	$r["log"] = array_reverse($log);
        $r["type"] = $d->log;
        $r["result"] = "success";
        $r["message"] = "Most Recent Alike $d->log Logfile";
	returnJSN($r);
//	}
}


function getStatusMgr(){
	$r = array();
	$r["systemStatus"] = "running";
	if(!file_exists("/mnt/share/docker.up")){
		$r["systemStatus"] = "loading";
	}

	$sub = getSubInfoMrg();
	$r["a3Name"] = "A3 Manager";
	$nfoFile = $GLOBALS['settings']->alikeRoot ."/alike.bld.nfo";
	$nfo = json_decode(file_get_contents($nfoFile), false);
        $r["alikeBuild"] = $nfo->alikeBuild;
        $r["alikeVersion"] = $nfo->alikeVersion;

        $r["alikeEdition"] = "5";
        $r["isTrial"] = "true";
        $r["isSubscription"] = "false";
        $r["daysLeft"] = "0";
        $r["latest_build"] = getSettingMgr("latest_build");
        $r["isOC"] = "0";
        $r["runningJobs"] = getActiveJobsMgr();
        $r["notifications"] = getAlerts();
        $r["cpuPerc"] = getCPUUsage();
        $r["numCPU"] = getCPUCount();
        $mem = getMemory();
        $r["srvMemTotal"] = $mem->total;
        $r["srvMemFree"] = $mem->free;
        $r["netUsage"] = getNetUsage();
        $r["site0Usage"] = getNetUsage("0");
        
	$r["virtualStored"] = getVirtProtected();
        $r["actualStored"] = getActStored();
        $r["dedupPerc"] = getADSDedup();
//	$r["activeVMs"] = getVMsInJobs();

	$r["_promoSeen"] = 1;
	$r["showInitialWelcome"] = "false";
	
	$r["ADSDefined"] = "true";
	$r["ADSReady"] = "OK";
	$state = "running";

        $r["serviceState"] = $state;
	
	// Some Java purge/maint stuff
	$java = getJavaStatus();
	$r["maintStats"] = $java;
	$r["status"] = "success";

	return $r;	// this doesn't get called directly
}


function getNotifications(){
	$cache = checkWsCache(0,time());
        if($cache == null){
		$secAgo =  60 * 60 * 24 * 14; // 2 weeks
		$r = array();
		$notes = getJobLogs(0, $secAgo);
		$r["notifications"] = $notes;
		$r["message"] = "Notifications";
		$r["result"] = "success";

                $cache = json_encode( $r);
                writeToCache(0, $cache);
        }
	$str = gzencode($cache);
	header("Content-type: text/javascript");
	header('Content-Encoding: gzip');
        print $str;

}

function doStatusMgr(){
//	$r = getStatus();
	$r = array();
	$r["promoSeen"] = "true";
	$r["message"] = "this is the message";
	$r["result"] = "success";
	$r["status"] = "success";
	$r["systemStatus"] = "running";
	$r["alikeBuild"] = getA3Build();
	$r["sites"] = getA3Tokens();

	$a3s = getA3sDB();
	$running = 0;
	foreach($a3s as $a){ $running += $a->numJobs; }
	$alerts = 0;
	foreach($a3s as $a){ $alerts += $a->numAlerts; }
	$state = 0;
	$cnts = 0;
	foreach($a3s as $a){ 
		$state += $a->status;
		$cnts++;
	}
	if($state ==0){
		$r["serviceState"] = "stopped";	// all off (0)
	}else if($state == $cnts){
		$r["serviceState"] = "running";	// all on (1)
	}else{
		$r["serviceState"] = "partial";	// something else ?
	}
	$r["runningJobs"] = $running;
	$r["notifications"] = $alerts;

	$r["systemStatus"] = "success";
	$str = json_encode($r);
	if($str === false){ 
		print json_last_error();
	}else{
		print $str;
	}
}

function handleAlertsMgr($d, $verb){
	if($verb == "dismiss"){
		dismissAlertMgr($d->id);
		return returnResult(true,"Alert Dismissed");
	}else if($verb == "dismissAll"){
		$r = dismissAllAlertsMgr();
		return returnJSN($r);
	}else if($verb == "get"){
		$r["alerts"] = getAllAlerts();
		$r["result"] = "success";
		$r["message"] = "All Alerts";
		returnJSN($r);
	}else if($verb == "getFrom"){
		$r["alerts"] = getAllAlerts($d->id);
		$r["result"] = "success";
		$r["message"] = "All Alerts";
		returnJSN($r);
	}
	returnError("Alert request not recognized: $verb ". print_r($d,true) );
}

function listRibbon($d){
	$r["alerts"] = getAllAlerts();
	$r["jobs"] = getActiveJobsMgr();
	$r["result"] = "success";
	$r["message"] = "Ribbon Stats";
	returnJSN($r);
}



function handleABDMgr($d, $verb){
	if($verb == "deployAll"){
		return runCmdMgr("deployAll",null, null);
	} else if($verb == "deploy"){
		return runCmdMgr("deploy",$d->poolid, null);
	} else if($verb == "delete"){
		return runCmdMgr("delete",$d->poolid, null);
	} else if($verb == "cull"){
		return runCmdMgr("cull",$d->id, null);
	} else if($verb == "spawn"){
		return runCmdMgr("spawn",$d->id, null);
	} else if($verb == "diag"){
		return runCmdMgr("diag",$d->poolid, null);
	} else if($verb == "settings"){
		// useManualIP, useDHCP, xennet, poolid
		$useDHCP = true;
		if(isset($d->useManualIP)){
			$useDHCP=false;
		}
		updateABD($d->poolid, $d->xennet, $useDHCP);
		$r = array();
		$r["message"] = "ABD Settings";
		$r["result"] = "success";
		return returnJSN($r);

	}
	returnError("ABD request not recognized");
}

function handleLogsMgr($d, $verb){
	$fp = $GLOBALS['settings']->alikeRoot ."/logs";
	$fname = "A3.log";
        if($verb == "A3"){
		$fp = "/home/alike/logs/a3.log";
	}else if($verb == "Data"){
		$fname = "A3_data.log";
		$fp = "/home/alike/logs/engine.log";
	}else if($verb == "Syslog"){
		$fname = "A3_syslog";
		$fp = "/homa/alike/logs/syslog";
	}

	header("Content-Type: application/octet-stream");
	header("Content-Transfer-Encoding: Binary");
	header("Content-disposition: attachment; filename=\"$fname\"");
	echo readfile($fp);
	return;

}


function handleABDNetMgr($d, $verb){
	if($verb == "set"){
		$msg = "ABD network assigned";
		$result = "success";
		$res = setABDNet($d);
		if($res!=""){
			$result = "error";
			$msg = "Could not assign IP: $res";
		}
		$r = array();
		$r["message"] = $msg;
		$r["result"] = $result;
		return returnJSN($r);

	}else if($verb == "delete"){
		deleteABDNet($d->id);
		$r = array();
		$r["message"] = "Network removed";
		$r["result"] = "success";
		return returnJSN($r);
	}else if(isset($d->id)){
		$r = array();
		$r["ABDNet"] = getABDNet($d->id);
		$r["message"] = "Network removed";
		$r["result"] = "success";
		return returnJSN($r);
	}

	returnError("ABDNet request not recognized: ($verb) ". print_r($d,true) );
}

function handleScheduleMgr($d, $verb){
	return returnError("Manager should not handle schedules!: ($verb) ". print_r($d,true) );
	$id =0;
	if (isset($d->id)){ $id = $d->id;  }
	$verb = trim($verb);
	if($verb == "run"){
		$name="";
		 if ($id > 0){
			return runCmdMgr("runNow", $id, null);
		}else if (isset($d->name)){ 
			return runCmdMgr("runNow", $d->name, null);
		}
		returnError("Could not run Schedule ($verb $id $name). Incorrect info provided." );
	}else if ($verb == "set"){
		$s = setSchedule($d);
		$r["message"] = "Schedule Saved";
		$r["id"] = $s->scheduleID;
		$r["result"] = "success";
		return returnJSN($r);
	}else if ($verb == "delete"){
		 if ($id >0){
			deleteSchedule($d->id);
			return returnResult(true, "Schedule Deleted");
		}
	}else if ($verb == "status"){
		 if ($id >0 && isset($d->status)){
			$msg = "Schedule has been enabled";
			if($d->status ==0){
				$msg = "Schedule has been disabled";
			}
			setScheduleStatus($id, $d->status);
			return returnResult(true, $msg);
		}
	}else if($d->id > 0){
		$r = array();
		$r["Schedule"] = getSchedule($d->id);
		$r["message"] = "Schedule Details";
		$r["result"] = "success";
		return returnJSN($r);
	}
        returnError("Schedule request ($verb $id) not recognized");
}

// quickbackup, set, delete, 
function handleVMMgr($d, $verb){
	if ($verb == "quickbackup"){
		return runCmdMgr("quickBackup",$d->vmuuid, null);
		//return returnResult(false, "Quick Backup not implemented yet");
	}else if ($verb == "details"){
		$vm = getVMDB($d->uuid);
		$r = array();
		$r["VM"] = $vm;
		$r["message"] = "VM Details";
		$r["result"] = "success";
		return returnJSN($r);
	}else if ($verb == "set"){
		$msg = "VM has been updated";
		switch($d->type){
			case "offsiteversions":
				updateVM($d->vmuuid,"offsitemax",$d->value);
				break;
			case "onsiteversions":
				updateVM($d->vmuuid,"onsitemax",$d->value);
				break;
			case "authprofile":
				updateVM($d->vmuuid,"authprofile",$d->value);
				break;
			case "accessip":
				updateVM($d->vmuuid,"accessip",$d->value);
				break;
		}
		return returnResult(true, $msg);

	}else if ($verb == "delete"){
		deleteVM($d->id);
		return returnResult(true, "VM has been deleted");
	}
	returnError("VM request not recognized");
}

function handleVersionMgr($d, $verb){
        if($verb =="delete"){
		$site = 0;
		if(isset($d->offsite) && $d->offsite==1){ $site=1; }
		purgeVersion($d->vmid, $d->version, $site);
		$r = array();
		$r["vmid"] =$d->vmid;
		$r["result"]="success";
		$r["message"]="Backup has been deleted";
		return returnJSN($r);
//		return returnResult(true, "VM Version has been deleted");
        }else if($verb =="validate"){
		return runCmdMgr("validate",$d->vmid, $d->version, $d->site);
        }else if($verb =="retain"){
		$site=0;
		if(isset($d->offsite) && $d->offsite==1){ 
			$site=1;
		}
		$str= setVersionRetention($d->vmid, $d->version, $site);
		return returnResult(true, "VM Version $str");
        }
	returnError("Version request not recognized");
}

function handleVaultingMgr($d, $verb){
        if($verb =="runSystemJob"){
		return returnResult(false, "You can't run a manual OSV Maint job.");
        }else if($verb == "manualVault"){
		if($d->site == 1) {
			return runCmdMgr("reverseVaultVm",$d->vmid, $d->version, null);
		}
		return runCmdMgr("vaultVm",$d->vmid, $d->version, null);
        }else if($verb == "setState"){
		returnError("Vault SetState not implemented!");
        }else if($verb =="getState"){
		returnError("Vault getState not implemented!");
        }
	returnError("Vaulting request not recognized");
}
function handleJobMgr($d, $verb){
	$jid = 0;
	if(isset($d->jobID)){ $jid =$d->jobID; }
	else if(isset($d->id)){ $jid =$d->id; }
	if($verb == "delete"){
		if($jid ==0){ return returnError("Cannot delete JobID 0"); }
		deleteJobMgr($jid, $d->a3id);
		return returnResult(1, "The job has been deleted");
	}else if($verb == "cancel"){
		if($jid ==0){ return returnError("Cannot cancel JobID 0"); }
		return runCmdMgr("cancel", $jid, null, null);
	}
	returnError("Unclear WS command for Job ($verb)");
}
function handleHostMgr($d, $verb){
	$msg = "Doing a host thing";
	$result = "success";
	if($verb =="edit"){
		$msg = editHostMgr($d->host, $d->a3s);
	}else if($verb == 'add' ){
		try{
                        if(is_array($d->host)){
                                foreach($d->host as $h){
                                        $h->isLicensed = 1;
                                }
                        }else{
                                $d->host->isLicensed = 1;
                        }

			// this could also be a pool now
			if(is_array($d->host)){
				$msg = addHosts($d->host, $d->a3s);
			}else{
				$msg = addHost($d->host, $d->a3s);
			}

		}catch(Exception $ex){
			$err = $ex->getMessage();
			return returnError("Failed to add Host ($err) ". print_r($d->host, true));
		}
	}else if($verb == "delete"){
		$msg = "Host deleted";
		delHostMgr($d->guid);		// removes from everyone
	}else if($verb == "assign"){	// this is when a known host is assigned/removed from an A3
		$msg = "Host assignment changed";
		$a3s = getA3sForHost($d->guid);
		$cur = [];
		foreach($a3s as $a){
			array_push($cur, $a->guid);
		}
		$added = array_diff($d->a3s, $cur);
		$removed = array_diff($cur, $d->a3s);
		$h = getHostByGuid($d->guid);
		foreach($added as $a){
			$a3 = getA3ByGuid($a);
			addHostRemote($h, $a3->id);
		}
		foreach($removed as $r){
			$a3 = getA3ByGuid($r);
			$msg =delHostRemote($d->guid, $a3->id);
		}

	}else if( $verb =="refresh"){
		return runCmdMgr("refresh", $d->id, null, null);
	}else if( $verb =="license"){
		if(!isset($d->guid)){ return returnError("No guid provided for host license"); }

		$h = getHostByGuid($d->guid);

		if($d->license ==0){
			setHostLicense($d->guid, false);	
			$msg = "Host un-licensed";
		}else{
			setHostLicense($d->guid, true);	
			$msg = "Host licensed";
		}
	}else if(isset($d->id)){
		return getHostDetailsMgr($d->id);
	}else{
		return returnError("Unclear WS command for Host ($verb)");
	}
	$r = array();
	$r["message"] = $msg;
        $r["result"] = $result;
        returnJSN($r);
}


function listLicenses(){
	$r = array();
        $r["subscription"]= getSubInfoMgr();
        $r["usage"]= getLicenseUsage();
        $r["message"] = "Sub Licensing";
        $r["result"] = "success";
        returnJSN($r);
}


function handleGFSProfileMgr($d, $verb){
        $r = array();
        $msg = "";
        if($verb =="set"){
//                editGFSProfile($d);
		$good = convertWsGfs($d);
		setGFSProfile($good);
                $msg = "GFS Profile updated: ". print_r($good, true);
        }else if($verb == "delete"){
                delGFSProfile($d->id);
                $msg = "GFS Profile deleted";
        }else if(isset($d->id)){
                 $r["profile"] =getGfs($d->id);
		$msg = "No Error";
        }
        $r["message"] = $msg;
        $r["result"] = "success";
        returnJSN($r);
}

function listGFSProfilesMgr($d){
        $r = array();

        $r["profiles"]= getGFSProfiles();
        $r["message"] = "GFSProfiles";
        $r["result"] = "success";
        returnJSN($r);
}



function listDisksMgr($d){
        $r = array();

        $isUUID=false;
        $id = $d->vmuuid;
        if(isset($d->vmid)){
                $id = getVMUuid($d->vmid);
        }
	$v = new stdClass();
	$v->UUID = $d->vmuuid;
	getVMDisksForSchedule($v);

        $r["Disks"]= $v->Disks;
        $r["VM"]= $v;
        $r["uuid"]= $d->vmuuid;

        $r["message"] = "Versions";
        $r["result"] = "success";
        returnJSN($r);
}

function listXenTemplatesMgr($d){
        $r = array();

	$justDefault = false; // getting the defaults is super slow
	xen_connect($d->hid);
	$hoster = getHostMgr($d->hid);	
        $temps = getXenTemplates($hoster->poolID, $justDefault);
	asort($temps);
        $r["templates"]= $temps;

        $r["message"] = "Templates";
        $r["result"] = "success";
        returnJSN($r);
}


function listVersionsMgr($d){
	$r = array();

	$isUUID=false;
	$id = 0;
	if(isset($d->vmuuid)){
		$id =$d->vmuuid;
	}else if(isset($d->vmid)){ 
		$id = getVMUuid($d->vmid);
	}

        $h = getVersions($id );
        $r["VmVersions"]= $h;
        $r["VM"]= getVM($id);

        $r["message"] = "Versions";
        $r["result"] = "success";
        returnJSN($r);
	
}
function listBackupsMgr($d){
        $r = array();

	if(isset($d->vaults)){
		$r["vaults"] =1;
	}else if(isset($d->backups)){
		$r["backups"] =1;
	}

	$r["A3s"] = getA3sDB();
        $r["result"] = "success";
        returnJSN($r);
}
function listVMsByTagMgr($d){
        $r = array();
        $r["message"] = "VMs";
        $r["result"] = "success";
	$vms = getVmsByTag($d->q);
	$guys = array();
	foreach($vms as $v){
		$dood = getVM($v->uuid);
		$dood->Disks = getDisksForVM($v->uuid);
		$dood->totalSize =0;
		foreach($dood->Disks as $disk){ $dood->totalSize += $disk->size; }
		array_push($guys, $dood);
	}
	$r["VMs"] = $guys;
        returnJSN($r);
}

function listAgentsMgr($d){
	$r["Agents"] =  getAllAgentsDB();
        $r["message"] = "Agents";
        $r["result"] = "success";
        returnJSN($r);
}
function listVMsMgr($d){
        $r = array();

	$t=0;
	if(isset($d->showType)){
		$t = $d->showType;	// 0=all, 1=with any backup, 2=only vaulted, 10 = paginated
	}

	$limit =20;
	$offset = 0;

	if($t == 10){
		$offset = intval($d->q);
	}

	$r["total"] =0;
	$r["limit"] =$limit;
	$r["offset"] =$offset;

	if(isset($d->q) && $t != 10){
		$r["VMs"]= searchVMsDB($d->q);
		$r["total"]= count($r["VMs"]);
		$r["limit"]= intval($r["total"]);
        }else{
		$r["VMs"] = getAllVMsDB();
//		$out = getVMsPageDB($offset * $limit, $limit);
//		$r["VMs"]= $out->vms;
//		$r["total"]= $out->total;
	}

        if(isset($d->hostUUID)){	// only vms on this host
        }else if($t==0){
	//	$r["VMs"]= getAllVMs($q,0);
        }else if($t==1){	// all vms with backups anywhere
		//$v = getAllVMs($q,1);
        }else if($t==2){	// only vaults
	}else{			// just the vms that match the search
	}
        $r["message"] = "VMs";
        $r["result"] = "success";
        returnJSN($r);
}

function listHostsMgr($d){
	$out = getHostsDB();
	foreach($out as $h){
		$h->a3s = getA3sForHost($h->guid);
	}
	$r["hosts"]= $out;
        $r["message"] = "Hosts";
        $r["result"] = "success";
        returnJSN($r);
}

function listHostPools($d){
	$r = array();
	$h = array();
	$out = new stdClass();
	$out->a3s = getA3sDB();
	$h = getHostsDB();
	$pools = [];
	foreach($h as $host){
		if($host->type != 2){ continue; }
		if(!array_key_exists($host->poolid, $pools)){
			$pools[$host->poolid] = "Pool Name Here";
		}
	}
	foreach($out->a3s as $a){
		$a->hosts = $h;
		$a->pools = $pools;
	}
	$r["A3s"]= $out->a3s;
        $r["message"] = "Hosts";
        $r["result"] = "success";
        returnJSN($r);
}

function listSchedulesMgr($d){
	$r = array();

	try{
		$s = getSchedulesDB();

		$r["Schedules"]= $s;
		$r["message"] = "Schedules";
		$r["result"] = "success";
		$r["manager"] = 1;
		returnJSN($r);
	}catch(Exception $ex){
		if(strpos($ex->getMessage(), "busy") !== false){
		
			$r = array();
			$r["result"] = "error";
			$r["noDBs"] =1;
			$r["message"] = $test;
			return returnJSN($r);
		}
	}

}
function listABDsMgr($d){
	$r = array();
        $s = getABDs();
        $r["Pools"]= $s;
        $r["message"] = "ABD List";
        $r["result"] = "success";
        returnJSN($r);
}


function listJobDetailsMgr($d){
	$cache = checkWsCache($d->jobID, 0);
        if($cache == null){
		$r = array();
		$s = getJoblogDetails($d->jobID);
		$r["Job"]= getJob($d->jobID);
		$r["entries"]= $s;
		$r["message"] = "JobDetails";
		$r["result"] = "success";

		$cache = json_encode( $r);
		if($r["Job"]->status >= 6 ){
			writeToCache($d->jobID, $cache);
		}
	}
	$str = gzencode($cache);
	header("Content-type: text/javascript");
	header('Content-Encoding: gzip');
        print $str;
}

function listJobsMgr($d){
	$r = array();
	$r["manager"] = 1;

	$onlyActive = false;
	$limit = 20;
	$offset = 0;
	if(isset($d->limit)){ $limit = $d->limit; }
	if(isset($d->offset)){ $offset = $d->offset; }

	if(isset($d->active) && $d->active==1){ $onlyActive = true; }
	$s = "";
	if(isset($d->search)){ $s = $d->search; }

	$jobs = array();
	$cnt =0;
	if($onlyActive){
		$jobs =  getActiveJobsMgr();
		$cnt = count($jobs);
	}else{
		$tmp =  getJobsMgr($limit, $offset * $limit);
		$cnt = $tmp->total;
		$jobs = $tmp->jobs;
	}

	if($onlyActive){ 
		$r["onlyActive"] = "true";
	}else{ 
		$r["onlyActive"] = "false";
	}

	$r["jobs"] = $jobs;
        $r["offset"] = $offset;
        $r["limit"] = $limit;
        $r["total"] = $cnt;

	$r["message"] = "Here are your Jobs";
        $r["result"] = "success";
        returnJSN($r);
}

function updateSettingMgr($d, $verb){

	if($verb == "saveAll"){
		if(!isset($d->settings)){
			returnError("No settings provided to save!" );
		}
	
		// after this, we should sync any shared setting to all a3s
		saveSettings($d->settings);
		syncGlobalSettings();
		// do syncShared 
		$r["message"] = "Settings saved!";
		$r["result"] = "success";
		return returnJSN($r);
	}

	if($d->setting == "welcomeShown"){
		setSetting($d->setting, $d->value);
	}else if($d->setting == "debugLevel"){
		setSetting($d->setting, $d->value);
	}

	$r = array();
	setSettingMgr($d->setting, $d->value);
	syncDBs();
	$r["message"] = "Settings";
        $r["result"] = "success";
        returnJSN($r);
}

function getSettingsMgr($d){
	$r = array();
	$r["settings"] = getAllSettings();
	$r["mode"] = getNodeMode();
	$r["sub"] = getSubInfoMgr();
        $r["a3guid"] = getInstallID();
        $r["a3build"] = getA3Build();
	$r["message"] = "Settings";
        $r["result"] = "success";
        returnJSN($r);

}
function getHostDetailsMgr($id){
	$r = array();
	$h = getHostMgr($id);	
	$r["host"] = $h;
	$r["message"] = "Host";
        $r["result"] = "success";
        returnJSN($r);
}

function checkMgrDBs(){
	$res = "ok";
	if(!file_exists("/home/alike/Alike/DBs/manager.db")){
		$res = "No DB files found";
	}else{
		try{
			getSettingMgr("uiUser");
		}catch(Exception $ex){
			$res = "Settings DB not valid";
		}
	}
	return $res;
}
function runCmdMgr($cmd, $arg, $arg2, $arg3=null){
	if($cmd == "deployAll"){
		exec($GLOBALS["settings"]->scripts."/jobRunner -a  > /dev/null &");
		return returnResult(true, "Redeploying ABD template to all pools");
	}else if ($cmd == "deploy"){
		exec($GLOBALS["settings"]->scripts."/jobRunner -a $arg  > /dev/null &");
		return returnResult(true, "Redeploying ABD template to pool");
	}else if ($cmd == "delete"){
		exec($GLOBALS["settings"]->scripts."/jobRunner -d $arg  > /dev/null &");
		return returnResult(true, "Deleting ABD template from pool");
	}else if ($cmd == "cull"){
		exec($GLOBALS["settings"]->scripts."/jobRunner -x $arg  $arg2 > /dev/null &");
		return returnResult(true, "Culling ABDs");
	}else if ($cmd == "spawn"){
		exec($GLOBALS["settings"]->scripts."/jobRunner -w $arg  $arg2 > /dev/null &");
		return returnResult(true, "Culling ABDs");
	}else if ($cmd == "diag"){
		exec($GLOBALS["settings"]->scripts."/jobRunner -t $arg  > /dev/null &");
		return returnResult(true, "Running ABD pool");
	}else if ($cmd == "runNow"){
		exec($GLOBALS["settings"]->scripts."/jobRunner -s $arg  > /dev/null &");
		return returnResult(true, "Running Job Now");
	}else if ($cmd == "quickBackup"){
		exec($GLOBALS["settings"]->scripts."/jobRunner -q $arg  > /dev/null &");
		return returnResult(true, "Running Quick Backup");
	}else if ($cmd == "validate"){
		exec($GLOBALS["settings"]->scripts."/jobRunner -c $arg $arg2 $arg3 > /dev/null &");
		return returnResult(true, "Validating Backup Now");
	}else if ($cmd == "vaultVm"){
		exec($GLOBALS["settings"]->scripts."/jobRunner -v $arg $arg2 > /dev/null &");
		return returnResult(true, "Vaulting VM Now");
	}else if ($cmd == "reverseVaultVm"){
		exec($GLOBALS["settings"]->scripts."/jobRunner -u $arg $arg2 > /dev/null &");
		return returnResult(true, "Reverse Vaulting VM Now");
	}else if ($cmd == "cancel"){
		cancelJob($arg);
		return returnResult(true, "Cancelling job $arg");
	}else if ($cmd == "refresh"){
		exec($GLOBALS["settings"]->scripts."/jobRunner -g $arg  > /dev/null &");
		return returnResult(true, "Beginning meta-refresh");
	}else if($cmd == "instaboot") {
		syslog(LOG_INFO, "About to call jobRunner with -i $arg");
		exec($GLOBALS["settings"]->scripts."/jobRunner -i $arg  > /dev/null &");
		return returnResult(true, "Beginning instant boot");
	}
}

function listInstabootsMgr(){
        $r = array();
	$t=0;
	$result = "error";
	$stuff = "";
	$sr = "";
	$msg = "VMs";
	try{
		$stuff = listInstaVmHosts();
		foreach($stuff as &$dangerous) {
			//$barf = print_r($dangerous, true);
			//syslog(LOG_INFO, "Dangerous is: $barf");
			$u = $dangerous["vm"];
			$rez = trim(xen_call("vm-param-get", "uuid=$u", "param-name=\"power-state\""));
			if(stripos($rez, "invalid") !== false) {
				syslog(LOG_ERR, "Cannot query power state for VM '$u' with error $rez");
			} else {
				$dangerous['power'] = $rez;
			}
		}
		$sr = getDiskUsageSR();
		$result= "success";
	}catch(Exception $ex){
		$msg = $ex->getMessage();
	}
	$r["SR"] = $sr;
	$r["VMs"]= $stuff;
        $r["message"] = $msg;
	$r["result"] = $result;

        returnJSN($r);
}

function xenPoolHostsMgr() {
	$pools = array();
	$returnMe = array();
	$hs = getHosts();
	foreach($hs as $h) {
		if($h->type != 2) {
			continue;
		}
		$poolID = $h->poolID;
		if(array_key_exists($poolID, $pools) == false) {
			$foo = array();
			$foo['poolID'] = $poolID;
			$pools[$poolID] = obtainPoolName($h->hostID, "__masterless");
		}
		$poolName = $pools[$poolID];
		$h = (array) $h;
		$h['poolName'] = $poolName;
		$h = (object) $h;
		array_push($returnMe, $h);
	}
	

	$r = array();

	$r["Hosts"] = $returnMe;
	$r["message"] = "hosts";
	$r["result"] = "success";

	returnJSN($r);
}

function doBrowseFLRMgr($j){
	$siteid=0;
	if($j->siteid!=""){ $siteid = $j->siteid; }

	$base = "/mnt/restore/$siteid";
	$len = strlen($base);
	$want = $j->path;
	if (substr($want, 0, $len) === $base) { $want = substr($want, $len); }	 // remove the base, just in case

	// check if they're going up a dir
	if (substr($want, -2) === '..') {
		$want = substr($want, 0, -3);
//		if (substr($want, -1) === '/') { rtrim($want, '/'); }
		$parts = explode("/", $want);
		$lastIndex = count($parts) - 1;
		unset($parts[$lastIndex]);
		$want = implode('/', $parts);
	}
	$path = $base . "/$want";
	$dir = listDir($path);

	foreach($dir as &$d){
		$d["name"] = basename($d["name"]);
	}

	$r["data"]=$dir;
	$r["siteid"]=$siteid;
	$r["result"] = "success";
	$r["relpath"]=$want;

	returnJSN($r);

}
function downloadFlrFileMgr($j){

	return flrDownload($j->js, $j->path);
	
}	

function handleSite($j, $verb){
        $r = array();
	$r["message"] = "A3 Handler";
        $r["result"] = "success";
	if($verb == "sync"){	
		syncA3ById($j->site);
	}
        returnJSN($r);
}

//function handleA3($j, $verb){
//        $r = array();
//	$r["message"] = "A3 Handler";
 //       $r["result"] = "success";
//	if($verb == "add"){	
//		$id = doAddA3($j->guid, $j->ip, $j->pass, $j->name);
//		$a3 = getA3ByGuid($j->guid);
//		$mip = getSettingMgr("managerIP");		// do we have this setting?
//		setRemoteSetting($id, "managerIP", $mip);
//		// pull in and renumber any remote gfs profiles
//		$s = getGfsRemote($a3);
//		$more = "";
//		try{
//		absorbRemoteGfs($s->gfsProfiles, $a3->id);
//		pushGfsToAll();	// push any new profiles to all existing A3s
//		// sync their vms, hosts, and Info
//		syncA3Info($a3);
//		syncHosts($a3);
//		syncVms($a3);
//		// the rest will sync in cron, let's get this host added quickly
//		syncGlobalSettings();
//		}catch(Exception $ex){
//			$more = $ex->getMessage();
//		}
//	
//		// TODO: set our hostIP as their managerIP remotely
//
//		$r["message"] = "A3 Added $more";
//	}else if ($verb == "edit"){
//		doEditA3($j->id, $j->ip, $j->pass);
//		$ip = getSettingMgr("hostIP");
//		syncGlobalSettings();
//		$r["message"] = "A3 Updated";
//	}else if ($verb == "delete"){
////		setRemoteSetting($j->id, "managerIP", "");
//		delA3($j->id);
//		$r["message"] = "A3 Detached";
//	}else if ($verb == "test"){
//
//		$res = testA3($j->ip, $j->pass );
//		if($res === false){
//			$r["result"] = "error";
//			$r["message"] = "Test Failed";
//		}else if($res== "success"){
//			$r["guid"] = $res->guid;
//			$r["name"] = $res->name;
//			$r["result"] = "success";
//			$r["message"] = "Test Passed !";
//			if($res->nodeMode != 1){
//				$r["result"] = "error";
//				$r["message"] = "A3 is not in Headless Mode (Their Mode: ". $res->nodeMode;
//			}
//		}else{
//			$r = $res;
//			if(isset($res->nodeMode) && $res->nodeMode != 1){
//				$r->result = "error";
//				$r->message = "Remote A3 is not in Headless Mode (Their Mode?:". $res->nodeMode;
//			}
//		}
//	}else if ($verb == "set"){
//		if(isset($j->name)){
//			setA3Name($j->id, $j->name);	
//		}
//	}
//        returnJSN($r);
//}

function getJobGraphMgr(){
        $r = array();
        $r["result"] = "success";
        $r["deltas"] = [];
        $r["dedup"] = [];
        $r["protected"] = [];
        $r["dates"] = [];
	$fn = "/tmp/dash.stats";
	if(file_exists($fn)){
		$dt = json_decode(file_get_contents($fn));
		$r["deltas"] = $dt->deltas;
		$r["dedup"] = $dt->dedup;
		$r["protected"] = $dt->protected;
		$r["dates"] = $dt->dates;
	}

        returnJSN($r);
}

function getDashData($j, $verb){
        $r = array();

        $r["stats"] = array();
	$mode = getNodeMode();
	$r["mode"] = $mode;
	// status:
	// nginx, java, blkfs, alikeSR, build, 
	// subInfo (sockets, edition, billdate, status, company, account owner)
        $ads = getADSInfo();
        $ods = getDSInfo(1);
        $mem = getMemory();
        $r["adsFree"] = $ads->free;
        $r["adsTotal"] = $ads->total;
        $r["adsUsed"] = $ads->total - $ads->free;

        $r["odsStatus"] = isODSMounted();
        $r["odsFree"] = $ods->free;
        $r["odsTotal"] = $ods->total;
        $r["odsUsed"] = $ods->used;
	$r["cpuPerc"] = getCPUUsage();

        $r["localDisk"] = getDiskUsageLocal();
        $r["dbDisk"] = getDiskUsageSR();
	$r["a3s"] = getA3sDB();

	if($mode == 2){
		$r["javaState"] = shell_exec("pgrep java") !== null;
		$r["blkfsState"] = shell_exec("pgrep blkfs") !== null;
		$r["instafsState"] = shell_exec("pgrep instafs") !== null;
	}

        $r["memTotal"] = $mem->total;
        $r["memFree"] = $mem->free;

        $r["guid"] = getInstallID();
        $r["build"] = getA3Build();
        $r["subInfo"] = getSubInfoMgr();
        $r["message"] = "Dash Data";
        $r["result"] = "success";
        returnJSN($r);
}


function listA3s(){
        $r = array();
        $s = getA3sDB();
        $r["A3s"]= $s;
        $r["message"] = "A3 List";
        $r["result"] = "success";
        returnJSN($r);
}

function handleAgent($j, $verb){
        $r = array();
	// we need to set the remote a3's vm.hostID field to that a3's guid
	// if the agent is licensed, set the a3's vm.poolID to 'licensed'
	if($verb == "assign"){	
		reAssignAgent($j->uuid, $j->a3id);	
		$r["message"] = "Agent reassigned";
		$r["result"] = "success";
	}else if($verb == "license"){
		$r =licenseAgent($j->guid );
	}else if($verb == "unlicense"){
		$r =unlicenseAgent($j->guid);
	}else if($verb == "delete"){
		$r =deleteAgentMgr($j->guid);
	}
        returnJSN($r);
}

function proxyLicensing($j){
	$j = json_decode(substr($j,5));
        $r = array();
	$msg = "A3 Licensing";
	$result = "success";
	if(!isset($j->machineID)){
		$msg = "No A3 Guid provided $verb ". print_r($j, true);
		$result = "error";
	}else{
		$out = proxySubSync($j->machineID, $j);
		$r["data"] = $out;
	}
	$r["message"] = $msg;
        $r["result"] = $result;
        returnJSN($r);
}
function proxyEmail($j){
	$guy = substr($j,5);
	$guy = urldecode($guy);
	$guy = json_decode($guy);
        $r = array();
	$msg = "A3 Email";
	$result = "success";
	if(!isset($guy->machineID)){
		$msg = "No A3 Guid provided for email ";
		$result = "error";
	}else{
		$out = proxySubEmail($guy->machineID, $guy->message);
		$r["data"] = $out;
	}
	$r["message"] = $msg;
        $r["result"] = $result;
        returnJSN($r);
}
function proxySubUsage($j){
        $j = json_decode(substr($j,5));
        $r = array();
        $msg = "A3 Usage";
        $result = "success";
        if(!isset($j->machineID)){
                $msg = "No A3 Guid provided for MSP usage ". print_r($j, true);
                $result = "error";
        }else{
                $out = proxySubMspUsage($j->machineID, $j->message);
                $r["data"] = $out;
        }
        $r["message"] = $msg;
        $r["result"] = $result;
        returnJSN($r);
}
function proxyA3Settings($j){
        $j = json_decode(substr($j,5));
        $r = array();
        $msg = "A3 settings backup";
        $result = "success";
        if(!isset($j->machineID)){
                $msg = "No A3 Guid provided for settings backup ". print_r($j, true);
                $result = "error";
        }else{
                $out = proxyA3SettingBackup($j->machineID, $j->message);
                $r["data"] = $out;
        }
        $r["message"] = $msg;
        $r["result"] = $result;
        returnJSN($r);
}

function proxyUpdateCheck(){
        $r = array();
        $msg = "A3 Update Check";
        $result = "success";
	$out = proxySubUpdateCheck();
	$r["data"] = $out;
        $r["message"] = $msg;
        $r["result"] = $result;
        returnJSN($r);

}
function checkShouldSync($site, $req, $verb){
	if($site==0){ return false; }
	if($req == "schedule"){
		if($verb == "run"){ return "job"; }
	} else if($req == "job"){
		if($verb == "run"){ return "job"; }
		if($verb == "delete"){ return "job"; }
		if($verb == "cancel"){ return "job"; }
	} else if($req == "abd"){
		if($verb == "diag"){ return "job"; }
		if($verb == "cull"){ return "job"; }
	} else if($req == "restore"){
		return "job";
	} else if($req == "version"){
		if($verb == "validate"){ return "job"; }
	} else if($req == "vm"){
		if($verb == "quickbackup"){ return "job"; }
	}

	return false;
}

?>




