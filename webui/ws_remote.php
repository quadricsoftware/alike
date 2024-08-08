<?php
include_once("/usr/local/sbin/shared_lib");
include_once("/usr/local/sbin/common_lib");
include_once("/usr/local/sbin/ws_lib");

// This must be handled before session creation to avoid deadlocking the browser
if (isset($_REQUEST["session"]) && isset($_REQUEST["file"]) && isset($_REQUEST["siteid"])){

	// check our session against the official QS WS session token
	$sess = $_REQUEST["session"];
	if ($sess != trim(file_get_contents("/tmp/qs.sess")) ){
		return returnError("Authentication Required");
	}
	return downloadFlrFile($_REQUEST);
}

session_start();

$dir = dirname($_SERVER['PHP_SELF']);
$thePage = $_SERVER['PHP_SELF'];

$appBase =  $_SERVER['DOCUMENT_ROOT'] . '/';

include_once("/usr/local/sbin/insta_common");
include_once("/usr/local/sbin/xen_common");

$isLoggedIn=false;
$userid=0;
$thePage = ltrim($thePage,"/");


if(!isset($_REQUEST['req'])){ return "No method provided"; }

$req = $_REQUEST['req'];


if($req == "logout"){
	session_destroy();
	return;
}

if(!isset($_REQUEST["session"]) && $req != "auth" && !isset($_REQUEST["manager-token"]) && $req != "agent-register"){
	return returnError("Authentication Required");
}
if($req == "agent-register" ){
	$mode = getNodeMode();
	$r = array();
	$r["guid"] = "";
	$res = "error";
	$msg = "";
	if($mode == 0){
		$r["isManager"] = 1;
		$msg = "Please register with an A3 Node.  We're just a manager.";
		$res = "error";

	}else{
		$msg = "Agent registered successfully.";
		$res = "success";
		$r["isManager"] = 0;
		$j = null;
		$ip = null;

		if(isset($_REQUEST['data'])){ 
			$j = json_decode($_REQUEST['data']); 
			if(isset($j->ip)){ $ip = $j->ip; }
		}else if(isset($_REQUEST["ip"])){
			$ip = $_REQUEST["ip"];
		}
		if(empty($ip)){
			$res = "error";
			$msg = "No IP Address provided";
		}else{
			$resp = requestAgentRegister($ip );
			return returnJSN($resp);
		}

//		$res = $resp->result;
//		$msg = $resp->message;
//		$r["guid"] = $resp->guid;
	}
	$r["result"] = $res;
	$r["message"] = $msg;
	return returnJSN($r);

}
// special check for adding A3s before we know their guid
if($req == "authTest" ){
	$tok = getSetting("a3ManagerPass");
	if($tok != $_REQUEST["manager-token"]){
		return returnError("Invalid token");
	}
	$r = array();
	$r["result"] = "success";
	$r["a3Name"] = "A3";
	$r["a3guid"] = getInstallID();
	$r["nodeMode"] = getNodeMode();
	return returnJSN($r);
}

if (isset($_REQUEST["manager-token"])){
	$a3 = new stdClasS();
	$a3->guid = getInstallID();
	$a3->password = getSetting("a3ManagerPass");
	$tok=  getA3Token($a3);
	if($tok != $_REQUEST["manager-token"]){
		return returnError("Invalid manager token provided $tok vs ". $_REQUEST['manager-token']);
	}
}

#if($_REQUEST["session"] != session_id()){
#	return returnError("Authentication Required");
#}
$test = "";
try{
	$test = checkDBs();
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

$verb = array_search("",$_REQUEST);

//$msg = print_r($req, true). " verb: $verb, j: ". print_r($j, true);
//syslog(LOG_DEBUG, "Processing Manager request: $msg");

try{
	return processNimbusRequest($req, $verb, $j);
}catch(Exception $ex){
	$msg = "Webservice encountered an error: ". $ex->getMessage();
	$msg .= $ex->getTraceAsString();
	exec("echo '$msg' >> /tmp/barf.txt");
	return returnError($msg);
}

#############################################################################################
####		Its all functions from here on down
#############################################################################################

function proccessRequest($req, $verb, $j){
	switch($req){
		case "jobgraph":
			return getJobGraph();
			break;
		case "a3-sysgraph":
			return getA3SysGraph();
			break;
		case "status":
			return doStatus();
			break;
		case "a3-status":
			return getA3Stats();
			break;
		case "a3-details":
			return getA3Detail();
			break;
		case "networks":
			return getNetworks();
			break;
		case "srs":
			return getSrs();
			break;
		case "restore":
			return handleRestore($j, $verb);
			break;

//// new stuff above

		case "auth":
			return doAuth($j);
			break;
		case "dashStats":
			return getDashStatistics();
			break;
		case "perfStats":
			return getPerfStats();
			break;
		case "notifications":
			return getAlerts();
			break;
		case "serviceState":
			return setServiceState($j);
			break;
		case "abds":
			return listABDs($j);
			break;
		case "joblog":
			return listJobDetails($j);
			break;
		case "jobs":
			return listJobs($j);
			break;
		case "schedules":
			return listSchedules($j);
			break;
		case "hosts":
			return listHosts($j);
			break;
		case "versions":
			return listVersions($j);
			break;
		case "vms-tag":
			return listVMsByTag($j);
			break;
		case "vms":
			return listVMs($j);
			break;
		case "sync-vms":
			return listVMsForMgr($j);
			break;
		case "sources":
			return listSources($j);
			break;
		case "vaults":
			return listVaults($j);
			break;
		case "backups":
			return listBackups($j);
			break;
		case "settings":
			return getSettings($j);
			break;
		case "subscription":
			return listSubscription($j);
			break;
		case "authProfiles":
			return listAuthProfiles($j);
			break;
		case "gfsProfiles":
			return listGFSProfiles($j);
			break;
		case "disks":
			return listDisks($j);
			break;
		case "templates":
			return listXenTemplates($j);
			break;
//////////////////////////////////// All of these guys have 'sub' commands
		case "job":
			return handleJob($j, $verb);
			break;
		case "host":
			return handleHost($j, $verb);
			break;
		case "schedule":
			return handleSchedule($j, $verb);
			break;
		case "vm":
			return handleVM($j, $verb);
			break;
		case "version":
			return handleVersion($j, $verb);
			break;
		case "authProfile":
			return handleAuthProfile($j, $verb);
			break;
		case "gfsProfile":
			return handleGFSProfile($j, $verb);
			break;
		case "vaulting":
			return handleVaulting($j, $verb);
			break;
		case "abd":
			return handleABD($j, $verb);
			break;
		case "abdnet":
			return handleABDNet($j, $verb);
			break;
		case "support":
			return handleSupport($j, $verb);
			break;
                case "logsummary":
                        return handleLogSummary($j, $verb);
                        break;
		case "logs":
			return handleLogs($j, $verb);
			break;
		case "flrDownload":
			return flrDownload($j, $verb);
			break;
//////////////////////////////////////////////

		case "setting":
			return updateSetting($j, $verb);
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
			return runCmd("autotune",null, null);
			break;
		case "viewlog":
			return getLog($j);
			break;
		case "alerts":
			return handleAlerts($j, $verb);
			break;
		case "assignAgent":
			return setAgent($j);
			break;
		case "deleteAgent":
			return delAgent($j);
			break;
		case "testAgentVM":
			return testAgentVM($j);
			break;
		case "testAgent":
			return testAgent($j);
			break;
		case "listInstaboots":
			return listInstaboots();
			break;
		case "instaboot":
			return handleInstaboot($j);
			break;
		case "megamaid":
			return srCleanup();
			break;
		case "xenPoolHosts":
			return xenPoolHosts();
			break;
		case "subsync":
			return doSyncSub();
			break;
		case "subupdate":
			return doSubUpdate();
			break;
		case "promoSeen":
			return doPromoSeen();
			break;
/////////////////////////////////////////////////////////////
		case "browseRestoreFS":
			return doBrowseFLR($j);
			break;
/////////////////////////////////////////////////////////////
	}
	returnError( "No such method: $req");
}

?>
