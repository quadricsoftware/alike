<?php
	$s = new stdClass();
	
	$s->alikeRoot = "/home/alike/Alike";
	$s->alikeHome = "/home/alike";
	$s->dbPath = "$s->alikeRoot/DBs";
	$s->appBase = "/var/www/html";
	$s->tempPath = "$s->alikeRoot/temp";
	$s->jvPath = "$s->alikeRoot/temp/jv";
	$s->adsPath = "$s->alikeRoot/ADS";
	$s->scripts = "$s->alikeRoot/hooks";
	$s->jobsPath = $GLOBALS["jobPath"];
	$s->logsPath = $GLOBALS["logPath"];

	$s->nimbusDB = "$s->dbPath/nimbusdb.db";
	$s->joblogDB = "$s->dbPath/nimbusjobs.db";
	$s->gfsDBfile = "$s->dbPath/gfs.db";

	$s->keyTool = "/home/alike/Alike/linbin/keytool";
	$s->alikeEdition =3;
	$s->alikeProduct =7;
	//$s->db = new PDO("sqlite:$dbFile");
	
	$GLOBALS['settings'] = $s;
?>
