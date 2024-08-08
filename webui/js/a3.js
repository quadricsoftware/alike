window.crumbs = []
window.wsTimeout = 5;
window.alikeEd = 3;

var job_cache = { jobID: 0, timestamp: 0 };


function showWelcomeModal(){
        prepModal("Welcome to Alike v7.5!");
	$("#main-modal-title").prepend("<img src='/images/alike-logo.png' alt=Alike Logo' >");
        $("#modal-ok-button").hide();
	let h =`
		<center><h4>Thank you for choosing Alike</h4></center>
		<br>
		If this is your first time using the A3, please be sure to refer to our <a class='qs_preview_link clickable'>QuickStart Guide for configuration steps</a>.
		<br>
		<br>As an overview:<br>
		<ul>
		<li>Assign your A3 in the Portal (Guid is on the Dash-->)</li>
		<li>Add any Hypervisor hosts you want to protect </li>
		<li>If you have technical questions, check the Support Page on the left, or contact support</li>
		<li>Please reach out to <a href='mailto:info@alikebackup.com'>info@alikebackup.com</a> if you have feedback or suggestions. </li>
		</ul>
		<center>We hope you enjoy Alike!</center>
		`;
        $("#modalBody").html(h);
        displayModal(true);
}

function setupPage(crumb, title, showSearch=false ){
	$("#header-controls").html("");
        clearTimer();
        $("#dataTableDiv").html('');
	$("#page-header").html(title);
	addCrumb(crumb)
	$("#page-table-header").html( showCrumbs() );
	$("#page-footer").html("");
	if(showSearch){
		$("#top-search").show();
	}else{
		$("#top-search").hide();
	}
	window.viewingJLD = false;
}
function adjustTableNav(table){
        table.width('99%');
        var guy = table.children().first();
        guy.removeClass("row");
        guy.addClass("d-flex flex-row-reverse");
        var guy2 = guy.children().eq(0);
        guy2.removeClass();
        guy2.addClass("d-flex");
        var guy3 = guy.children().eq(1);
        guy3.removeClass();
        guy3.addClass("d-flex");
        guy2.before(guy3);
        $("<div class='d-flex'> &nbsp; </div>").insertAfter(guy3)
}
function checkNoA3s(){	
	var a3s = JSON.parse(sessionStorage.getItem("a3Cache"));
	let a3Cnt = a3s.length;
	if(a3Cnt ==0){
		finishLineLoad();
                var row = "<div class='row bg-info m-2'><div class='col-12 panel-inactive rounded align-middle p-4' style='text-align:center;'><h3 >No A3s found!</h3><br><button class='btn btn-success a3-add'>Add an A3</button></div></div>";
                $("#dataTable").append(row);
                return false;
	}
	return true;
}
function runQuickBackup(id, a3id){
        $.confirm({
                content: "Run a quick backup of this system?<br><br><b>Note:</b><br>This will perform an agentless backup, unless the target is an agent-only system.",
                title: "Run Quick Backup?",
                draggable: true,
                closeIcon: false,
                icon: 'far fa-question-circle',
                buttons: {
                    confirm: function() {
			var args= new Object();
			args["uuid"] = id;
			wsCall("vm/quickbackup",args,"getActiveJobs",null, a3id);
                    },
                    cancel: function() { }
                }
        });
}

function cancelJob(id, site=0){
        $.confirm({
                title: "Confirm Job Cancel?",
                content: "Are you sure you want to cancel this Job?",
                draggable: true,
                closeIcon: false,
                icon: 'fa fa-warning',
                buttons: {
                    confirm: function() {
				var args= new Object();
				args["id"] = id;
				wsCall("job/cancel",args,null,null,site);
				doJLDRefresh(id, site);
                    },
                    cancel: function() { }
                }
        });
}

function doDeleteA3(id){
        $.confirm({
		content: "Are you sure you want to remove this A3?<br><br><b>Note:</b><br>The A3 will continue to operate, but will no longer be manager by this console.",
                title: "A3 detach Confirmation",
                draggable: true,
                closeIcon: false,
                icon: 'fa fa-warning',
		buttons: {
		    confirm: function() {
			    var args= new Object();
			    args["id"] = id;
				wsCall("a3/delete",args,"listA3s",null);
		    },
		    cancel: function() { }
		}
        });
}


function deleteSchedule(id, site=0){
        $.confirm({
		title: "Confirm Schedule Deletion?",
		content: "Are you sure you want to delete this Scheduled Job?",
		draggable: true,
		closeIcon: false,
		icon: 'fa fa-warning',
		buttons: {
		    confirm: function() {
			    var args= new Object();
			    args["id"] = id;
				wsCall("schedule/delete",args,null,null, site);
				drawSchedulesTable();
		    },
		    cancel: function() { }
		}
        });
}


function getJobDetails(jobID, site, lastTs=0){
	var data =sessionStorage.getItem("jobid_"+site+"_"+jobID);
	if(data != null){
		return drawJoblog(JSON.parse(data), site);
	}
	var showLoad=true;
        if($("#jobid").length){
                if($("#jobid").attr("data-id") == jobID){ 
			showLoad=false;
		}	// we're an update call
        }
	if(showLoad){ showLineLoad(); }

//	if(lastTs ==0 && job_cache.jobID == jobID){ lastTs = job_cache.timestamp; }
	
        var args = new Object();
        args["jobID"] =jobID;
        args["since"] =lastTs;
	wsCall("joblog",args,"drawJoblog",site, site);
}


function drawA3sTable(data) {
	setupPage(" <a href='#' class='go_a3s'>Manage A3s</a>", "Manage A3s");
	var btn = "<button type='button' class='btn btn-info btn-block a3-add'><i class='fa fa-bell'> Add New A3</i></button> &nbsp;";
	$("#header-controls").html(btn);
        $("#dataTableDiv").append("<div id='dataTable'></div>");
        var head = "<table class='table table-sm table-bordered table-hover table-striped' id='a3-list-table'>";
        head += "<thead><tr><th>#</th><th>A3 Name</th><th>Address</th><th>Guid</th><th>Build</th><th>Status</th><th>Last Seen</th><th>Details</th><th>Alerts</th><th></th</tr></thead><tbody></tbody></table>";
	if(!checkNoA3s()){ return; }
        if(data.A3s.length ==0){
                var none = "<div class='row bg-info m-2'><div class='col-12 panel-inactive rounded align-middle p-4' style='text-align:center;'><h3 >There are no A3s defined yet</h3></div></div>";
                $("#dataTable").append(none);
		return;
        }
	$("#dataTable").append(head);
        jQuery.each(data.A3s, function(i,r){
		drawA3Row(r, $("#a3-list-table tbody"));
        });

}
function drawA3Row(r, element, showTrash=true){
                let row = "<tr>";
                var edit = "<span data-id="+r.id+" data-ip="+r.ip+" data-pass="+r.password+" class='a3-edit clickable'><i class='fas fa-wrench fa-lg' data-toggle='tooltip' title='Edit A3'> </i></span>";
                var trash = "<span data-id="+r.id+" class='a3-delete clickable'><i class='fas fa-trash-alt ' data-toggle='tooltip' title='Delete A3'></i></span>";
                let deets = "<span id='"+r.id+"-cpu-pie'></span> CPU &nbsp; <span id='"+r.id+"-mem-pie'></span> Memory &nbsp; <span id='"+r.id+"-ads-pie'></span> ADS";
                var alerts = "<i class='fas fa-check-circle fa-fw fa-lg' style='color:#097969;' title='No Alerts'></i>";
                if(r.numAlerts > 0){ alerts = "<div class='badge badge-pill badge-danger' style='font-size: 14px;'> "+r.numAlerts+"</div>"; }
                let head = "";
		if(showTrash){ head += "<td>"+edit+"</td>"; }
                head += "<td class='go_a3-details clickable' data-id="+r.id+"> <b>"+r.name+"</b> </td>";
                head += "<td class='go_a3-details clickable' data-id="+r.id+"> "+r.ip+"</td>";
                head += "<td class='go_a3-details clickable' data-id="+r.id+">"+r.guid+"</td>";
                head += "<td class='go_a3-details clickable' data-id="+r.id+">"+r.build+"</td>";
                head += "<td class='go_a3-details clickable' data-id="+r.id+">"+r.status+"</td>";
                head += "<td class='go_a3-details clickable' data-id="+r.id+">"+timeSince(getJSEpoch(r.lastSeen)) +"</td>";
                head += "<td class='go_a3-details clickable' data-id="+r.id+">"+deets+"</td>";
                head += "<td>"+alerts+"</td>";
		if(showTrash){
			head += "<td>"+trash+"</td>";
		}
                head += "</tr>";
                row += head;

                element.append(row);
                showMiniPie(r.id+"-cpu-pie", r.cpuFree);
                showMiniPie(r.id+"-mem-pie", (r.memFree/r.memory) * 100);
                showMiniPie(r.id+"-ads-pie", (r.adsFree /r.adsSize) *100 );

}

///////////////////////////////////// A3 Detail page ///////////////////////////////////////////////////
//
function drawA3Details(data, a3id) {
        if(data.result != "success"){
                doToast(data.message, "Failed to get A3 details", "error");
                return;
        }


        setupPage(" <a href='#' class='go_a3-details' data-id="+a3id+">A3 Info</a>", "A3 Details");

	var stats = data.stats;

	var adsPerc = (stats.adsFree / stats.adsSize) * 100;
	var odsPerc = (stats.odsFree / stats.odsSize) * 100;
	var memPerc = (stats.memFree / stats.memory) * 100;

        $("#dataTableDiv").append("<div id='dataTable'></div>");
	var row = `<div class='container-fluid'>

	<div class='row'>
	<div class='col-8 '>

	<div class='row'>

	<div class='chart-container' style='height: 150px; width:100%' >
	<canvas id='perfGraph' ></canvas></div>

	</div>
	<div class='d-flex justify-content-around'>`;

	let ads = prettyFloat(100 - adsPerc);
	let ods = prettyFloat(100 - odsPerc);
	let cpu = prettyFloat(100 - stats.cpuFree);
	let mem = prettyFloat(100 - memPerc);
	let nodsBox =`<div class="ribbon-wrapper"> <div class="ribbon bg-warning text-xs">&nbsp; DR Only &nbsp;</div> </div>`;
	let odsTip = 'Offsite DataStore requires DR Edition';
	nodsBox = ''; 
	odsTip = `ODS is ${ods}% full`;
	if(stats.odsStatus != true){
		nodsBox = ''; 
		odsTip = `ODS is not configured yet`;
	}
	row += `
			<div class='p-1' data-toggle='tooltip' title='ADS is ${ads}% full'>
				<center><div ><canvas id='adsPie' width='100' height='100'></canvas></div><span class='badge badge-secondary'>ADS Usage</span></center>
			</div>
			<div class='p-1' data-toggle='tooltip' title='${odsTip}'>
				<div class="position-relative" > ${nodsBox} <center><div >
				<canvas id='odsPie' width='100' height='100'></canvas></div><span class='badge badge-secondary' id='odsLabel'>ODS Usage</span></center>
				</div>
			</div>
			<div class='p-1' data-toggle='tooltip' title='Sys Volume ${ads}% full'>
				<center><div ><canvas id='a3sysPie' width='100' height='100'></canvas></div><span class='badge badge-secondary'>System Volume</span></center>
			</div>
			<div class='p-1' data-toggle='tooltip' title='ADS is ${ads}% full'>
				<center><div ><canvas id='srPie' width='100' height='100'></canvas></div><span class='badge badge-secondary'>Alike SR Usage</span></center>
			</div>
			<div class='p-1' data-toggle='tooltip' title='CPU Usage'>
				${showKnob("cpu", cpu,"CPU%")}
			</div>
			<div class='p-1' data-toggle='tooltip' title='Memory in use'>
				${showKnob("mem", mem,"Mem Used%")}
			</div>
	</div>

	<br><div class='container'>`;

	var logBody = `<table id='a3-minilog' data-site='"+a3id+"' width=100%>
		<tr><td colspan=2><h4>Loading alerts and warnings...</h4></td></tr>
		</table>`;

	var logHead = `
	<div class='row'><div class='col-2'>
	<select id='a3-minilog-drop' class='custom-select' data-site="${a3id}">
	<option value='alerts'>Alerts</option>
	<option value='all'>All Urgent</option>
	<option value='engine'>Data Engine</option>
	<option value='a3'>Application Log</option>
	<option value='syslog'>System Log</option>
	</select>
	</div>
		<div class='col p-2'> <i class='fas fa-download download-log clickable' data-site="${a3id}" data-toggle='tooltip' title='Download entire log file' ></i> 
		<i style='padding-left: 15px;' class='fas fa-archive support-archive clickable' data-site="${a3id}" data-toggle='tooltip' title='Download Support Archive' ></i> 
		</div>
	</div>`;

	row += printPanel(logHead, logBody, "panel-secondary");

	row += `</div> </div>

		<div class='col-4 '>

	<div class='container'>`;
	var jobBody = "";
	$.each(stats.runningJobs,function(i,j){
		jobBody += "<div data-id='"+j.jobID+"' data-site='"+a3id+"' class='go_joblog clickable row'><div class='col-4 text-nowrap text-truncate'>"+j.name+"</div><div class='col'> "+ makePB(Math.round(j.progress), j.status) +"</div> </div>";
		// status, timeBegin, timeEnd
	});
	row += printPanel("Active Jobs", jobBody, "panel-secondary");
	row += "</div>";

	row += "<div class='container'>";

	var info =`
		<table><tr>
		<td style='white-space: nowrap;'>Alike Build: </td><td colspan=3><b><span id='a3-build'>${stats.build}</span></b></td></tr>
		<td>Address: </td><td colspan=3><b><span id='a3-ip'>${stats.hostIP}</span></b>
		</td></tr>
		<tr><td>
		GUID: </td><td colspan=3><b><span id='a3-guid'>${stats.guid}</span></b>
		</td></tr>`;

	// service status(s)
	let javaStat ="badge-success", blkfsStat = javaStat, instafsStat = javaStat;
	if(!stats.instafsState){ instafsStat = "badge-danger"; }
	if(!stats.blkfsState){ blkfsStat = "badge-danger"; }
	if(!stats.javaState){ javaStat = "badge-danger"; }
	info += `<tr>
		<td> Services </span> </td>
		<td> <span class='badge ${javaStat}'> Engine </span> </td>
		<td> <span class='badge ${blkfsStat}'>  RestoreFS </span> </td>
		<td> <span class='badge ${instafsStat}'> AlikeSR </span> </td>
		</tr>`;

	// engine status
	info += "<tr>";
	var estat = "<td colspan=3><span class='badge badge-success'>Ok</span></td>";
	if(stats.engineStats.rebuildActive){
		estat = `<td><span class='badge badge-warning'>Rebuilding</span> </td>
			<td colspan=2>
			${makePB(clamp(Math.round(stats.engineStats.rebuildPercent),0,100), "bg-info")}
			</td>`;
	}
	info += "<td style='white-space: nowrap;'>DB Cache</td>"+estat ;
	info += "</tr>";

	var adsStat = "badge-success";
	if(stats.adsStatus != "OK"){ adsStat = "badge-danger"; }	
	info += `<tr>
		<td style='white-space: nowrap;'> ADS Status:</td><td colspan=3> <span class='badge ${adsStat}'>${stats.adsStatus}</span> </td>
		</tr>`;

	var curTx= prettyNet(stats.adsNet[stats.adsNet.length -1].tx);
	var curRx= prettyNet(stats.adsNet[stats.adsNet.length -1].rx);

	info += `<tr id='net-box'>
	<td ><span style='color: #00bf00' class='text-nowrap'><i class='fas fa-arrow-up fa-lg'></i> <span id='cur-tx'>${curTx}</span></span><br>
	<span style='color: #007bff' class='text-nowrap'><i class='fas fa-arrow-down fa-lg'></i> <span id='cur-rx'>${curRx}</span> </span></td>
		<td colspan=3> 
		<span id='net-tx'></span>
		<span id='net-rx'></span>
		</td>
	</tr>

	</table>`;

	row += printPanel("A3: "+ stats.name, info, "panel-info");
	row += `</div>

	<div class='container'>`;

	let sb='';

	sb += `
	<div class='row'><div class='col'>
	<div class='row'><div class='col'><span class='none'>A3 Hostname ${printTip("This sets the local unix hostname on the A3 container.  Requires a restart to take effect.")}</span></div>
	<div class='col'><input id='a3Hostname' type='text' class='form-control auto-setting' placeholder='A3.local' data-site=${a3id} value='a3'></div></div>
	</div></div>

	<div class='row'><div class='col'>
	<div class='row'><div class='col'><span class='none'>A3 Host IP 
	${printTip("The IP address used for external agents and systems to access this IP.<br><br><u>This is a critical setting</u>, <b>be very careful changing it</b>")}
	</span></div>
	<div class='col'><input id='hostIP' type='text' class='form-control auto-setting' placeholder='x.x.x.x of A3' data-site=${a3id}></div></div>
	</div></div>
	 <div class='row'><div class='col'>`;
	sb += printToggleSite("readOnlyMode","Read-only Mode", data.readOnlyMode, a3id, "custom-switch-on-danger auto-setting");
	sb += `</div></div>

	<div class='row'><div class='col'>`;
	sb += printToggleSite("showTraceMessages","Enable trace", data.showTraceMessages,a3id, "auto-setting");
	sb += `</div><div class='col'>
	${printTip("Trace logging can be helpful for troubleshooting, but will <i>greatly</i> increase your job log size.  Be sure to disable it when troubleshooting is complete.")}
	</div></div>`;

//	sb += `<div class='row'><div class='col'>`;
//	sb += printToggleSite("smtpNotify","Enable Job and Notification Emails", data.smtpNotify, a3id, "auto-setting test-email-toggler");
//	sb += `</div></div>`;
//
//	sb += `<div class='row' id='test-email-pane' style='display: none;'><div class='col p-2'>
//	<button class='btn btn-sm btn-primary' id='send-test-email' data-site=${a3id}>Send Test Email</button>
//	<div class='col' id='test-email-results'></div>
//	</div></div>`;


	row += printPanel("A3 Settings", sb, "panel-info");
	row += `</div>

		</div>
		</div>
		</div>`;

        $("#dataTable").append(row);

	if(stats.odsStatus != true){
		$("#odsPie").prop('disabled', true);
		$("#odsLabel").html("ODS Not Defined");
	}

	drawDiskPie(stats.adsSize , stats.adsFree, "adsPie", false);
	drawDiskPie(stats.odsSize , stats.odsFree, "odsPie", false);
	drawDiskPie(stats.localDisk.total , stats.localDisk.free, "a3sysPie", false);
	drawDiskPie(stats.dbDisk.total - stats.dbDisk.free, stats.dbDisk.free, "srPie", false);

	$(".knob").knob({'readOnly':true });
	finishLineLoad();

	wsCall("settings", null, "assignA3Settings", null, a3id );
	wsCall("jobgraph", null, "updateA3UsageChart", null, a3id );
	let args= new Object();
	args["id"] = a3id;
	wsCall("alerts/getFrom", null, "updateMinilog", null, 0 );
	//wsCall("logsummary/all", null, "updateMinilog", null, a3id );
	renderTips();

	let wide = $("#net-box").width() -50;
	let rx=[];
	let tx=[];
	$.each(stats.adsNet, function(i,d){ tx.push(d.tx); });
	$.each(stats.adsNet, function(i,d){ rx.push(d.rx *-1); });
	$("#net-tx").sparkline(tx, { fillColor: false,chartRangeMinX: 0, changeRangeMin: 0, chartRangeMax: rx.length ,lineWidth: 2,height: 50, width: wide, lineColor: '#00bf00' });
	$("#net-rx").sparkline(rx, { fillColor: false, chartRangeMinX: 0, changeRangeMin: 0, chartRangeMax: tx.length,lineWidth: 2,height: 50, width: wide, lineColor: '#007bff'  });
	pollSysUse(a3id, wide);
}

async function getA3NetUse(a3id, wide){
        try{
                var res = await wsPromise("a3-sysgraph", null, a3id)

		$("#cur-tx").html(prettyNet(res.adsNet[res.adsNet.length -1].tx));
		$("#cur-rx").html(prettyNet(res.adsNet[res.adsNet.length -1].rx) );

		let rx=[];
		let tx=[];
		$.each(res.adsNet, function(i,d){ tx.push(d.tx); });
		$.each(res.adsNet, function(i,d){ rx.push(d.rx *-1); });
		$("#net-tx").sparkline(tx, { fillColor: false, changeRangeMin: 0, chartRangeMax: rx.length,lineWidth: 2,height: 50,width: wide, lineColor: '#00bf00'  });
		$("#net-rx").sparkline(rx, { fillColor: false, changeRangeMin: 0, chartRangeMax: rx.length,lineWidth: 2,height: 50,width: wide, lineColor: '#007bff' 
		});
		let cpu = prettyFloat(100 - res.cpuFree);
		let memPerc = 100 -(res.memFree / res.memory) * 100;
		$("#cpu").val(cpu).trigger("change");
		$("#mem").val(memPerc).trigger("change");
		renderTips();
        }catch (ex){
		//doToast("Failed to get net: "+ex.message, "ARG");
        }

}
function pollSysUse(a3id, wide){
        if(window.a3NetTimer){ clearTimeout(window.a3NetTimer); }
	window.a3NetTimer = setInterval(function() {
		getA3NetUse(a3id, wide);
	}, 2000);
}

function updateA3UsageChart(data){
        if(data.dates){
                var dz =[];
                $.each(data.dates, function(i,d){ dz[dz.length]=getDateCust(d,false); });
                drawUsageGraph(dz,data.protected,data.deltas,data.dedup,"perfGraph");
        }else{
                $("#dash-usage-graph").html( "No data for graph to display yet." );     
        }
}


function testEmailCallback(data){
	if(data.result == "success"){
		var p = "Email sent successfully!";
		$("#test-email-results").addClass("bg-success");
		$("#test-email-results").html(p);
	}else{
		var p = "Failed to send Email<br>"+data.message;
		$("#test-email-results").addClass("bg-warning");
		$("#test-email-results").html(p);
	}
}

function assignA3Settings(data){
	$("#smtpNotify").prop('checked', isCheckedBool(data.smtpNotify) );
	if(isCheckedBool(data.smtpNotify) ){ $("#test-email-pane").show(); }
	$("#a3-build").val(data.build);
	$("#hostIP").val(data.hostIP);
	$("#a3Name").val(data.a3Name);
	$("#showTraceMessages").prop('checked', isCheckedBool(data.showTraceMessages) );
	$("#readOnlyMode").prop('checked', isCheckedBool(data.readOnlyMode));
}
function updateMinilog(data){
	var logBody = "";
	if(data.alerts){
		if(data.alerts.length ==0){
			logBody += "<tr><td colspan=2><h4>No Active Alerts Found</h4></td></tr>";
		}
		$.each(data.alerts,function(i,a){
			var cls = "alert ";
			var ts = a.timestamp;
			if(isNumeric(a.timestamp)){ ts= timeSince(a.timestamp); }
			logBody += `<tr class='"+cls+"'><td class='shrinkFit'  data-toggle='tooltip' title='${getDate(a.timestamp)}'>${ts}</td><td> ${a.message}</td></tr>`;
		});
		$("#a3-minilog").html(logBody);
		return;
	}

	$.each(data.entries,function(i,a){
		var cls = "alert ";
		if(a.status <= 1){cls += "alert-danger"; }
		else if(a.status <= 3){cls += "alert-warning"; }
		var ts = a.timestamp;
		if(isNumeric(a.timestamp)){ ts= timeSince(a.timestamp); }
		logBody += "<tr class='"+cls+"'><td class='shrinkFit'>"+ts+"</td><td>"+a.message+"</td></tr>";
	});
	if(data.entries.length ==0){
		logBody += "<tr><td colspan=2><h4>No urgent log messages found</h4></td></tr>";
	}
	$("#a3-minilog").html(logBody);

}



function drawJobsTable(data) {
	var act =false;
	if(data.onlyActive=="true"){ act=true; }

	var pg = "go_jobs-hist";
	var pn = "Job History";
	if(act){ 
		pg = "go_jobs-run"; 
		pn = "Running Jobs";
	}
	setupPage(" <a href='#' class='"+pg+"'>"+pn+"</a>", pn, true);
	// setupPage clears timers, so set ours after
        if( act){
                window.intervalTimerID = setTimeout(function() { getActiveJobs(); }, 5000);
        }

        $("#dataTableDiv").append("<div id='dataTable'></div>");
	if(!act){
		$("#dataTableDiv").append("<input type='hidden' id='jobHistory' />");
	}
        var row = "<table class='table table-sm table-bordered table-hover table-striped'>";
        row += "<thead><tr><th>Job Name</th><th class='mgr-only'>A3</th><th>Status</th><th>Progress</th><th>Type</th><th>Started</th><th>Total Time</th><th>Job Size</th><th>Amount Stored</th><th></th></tr></thead><tbody>";
        if(data.jobs.length ==0){
		let msg = 'No jobs have been run yet!';
		if(act){ msg = 'There are no jobs running currently.'; }
                var row = `<div class='row bg-info m-2 rounded'><div class='col-12 panel-inactive rounded align-middle p-4' style='text-align:center;'><h3 >${msg}</h3></div></div>`;
                $("#dataTable").append(row);
		return;
        }
        jQuery.each(data.jobs, function(i,r){
                var site=r.a3id;
                var showName = snipString(r.name, 64);
                var done = "N/A";
                if(r.status != JobStatus.active && r.status != JobStatus.activeWithErrors && r.status != JobStatus.pending && r.timeEnd !=0){ done = getDate(r.timeEnd); }
                var stDesc = getStatusDesc(r.status);
                var pb = "";
                var stat = "bg-success";
                var now = new Date().getTime() / 1000;
		done = timeElapsed(r.timeElapsed);
//                if(r.status != 0 && r.status != 6 && r.systemsInJob > 0){
                        var percComp = clamp(Math.round((r.progress ),0,100) );
                        percComp = 100;
                        if( r.status == JobStatus.failed){ stat = "bg-danger";}
                        else if(r.status == JobStatus.active){ stat = "bg-active"; }
                        else if(r.status == JobStatus.activeWithErrors){ stat = "bg-active"; }
                        else if(r.status == JobStatus.errored){ stat = "bg-errored"; }
			else if(r.status == JobStatus.cancelled){ stat = "bg-warning";}
//                        else if(r.status == JobStatus.complete && r.progress < 100){ stat = "bg-warning";}
			if(r.status != JobStatus.complete && r.status != JobStatus.error){
				var pg = r.progress;
				if(!isNumeric(pg)){ pg = 1; }
				pb = makePB(Math.round(pg), stat)  ;	// don't show the PB on completed jobs
			}
//                }
                let trash =`<i class='clickable fas fa-trash job-delete' data-id=${r.jobID} data-site=${site} title='Delete Job'> </i>`;
		if(r.status == JobStatus.active || r.status == JobStatus.activeWithErrors){ trash = ""; }
                row += "<tr data-jid='"+r.jobID+"' data-site="+site+">";
                row += "<td class='go_joblog clickable' data-id='"+r.jobID+"' data-site="+site+">["+r.jobID+"]  " + showName + "</td>";
                row +="<td  class='mgr-only' data-id="+r.a3id+"> " + r.a3Name + "</td>";
                row +="<td > <span class='badge "+stat+"'>" + stDesc + "</span></td>";
                row +="<td > " + pb + "</td>";
                row +="<td > " + getJobTypeIcon(r.type)+" " + getJobTypeDesc(r.type) + " </td>";
                row +="<td > " + getDate(r.timeBegin) + "</td>";
                row +="<td > " + done + "</td>";
                row +="<td > " + prettySize(r.totalSize) + "</td>";
                row +="<td > " + prettySize(r.finalSize) + "</td>";
                row +=`<td > ${trash} </td>`;
                row += "</tr>";

        });
        row += "</tbody>";
        $("#dataTable").append(row);

        $(".job-delete").on("click", function() {
		let theSite = $(this).data("site");
		let args= new Object();
		args["id"] = $(this).data("id");
		args["a3id"] = theSite;
		
		wsCall("job/delete",args,null,null);
		$(this).closest('tr').remove();	
        });


	var prev = data.offset -1;
	if(prev <0){ prev = 0;}
	var searchNav = "<div style='text-align:center;'>";
	searchNav += "<div class='btn-group'>";
	if(data.total > data.limit && data.offset > 0){
		searchNav += "<button class='btn btn-outline-primary btn-sm go_jobs-search' data-id="+prev+">Previous</button>";
	}else if(data.total > data.limit){
		searchNav += "<button class='btn btn-outline-secondary btn-sm'>Previous</button>";

	}
	data.offset= parseInt(data.offset);
	data.limit= parseInt(data.limit);
	data.total= parseInt(data.total);

	var numPages = Math.ceil(data.total / data.limit);
	var end = data.total;
	var boxLimit = 10;
	
	if(data.offset > 1){
		searchNav += "<button class='btn btn-outline-primary btn-sm go_jobs-search' data-id=0>1</button>";
		searchNav += "<button class='btn btn-outline-secondary btn-sm'>...</button>";
		boxLimit -=1;
	}

	var startPos = data.offset;
	if( (numPages - startPos) < boxLimit){ 
		startPos = numPages - boxLimit; 
	}
	if(startPos < 0){ startPos = 0; }

	if(data.total > data.limit){
		for (var i = startPos; i < boxLimit + data.offset; i++) {
			if(i >= numPages){ 
				break; 
			}
			if(i == data.offset){
				searchNav += "<button class='btn btn-primary btn-sm go_jobs-search' data-id="+i+">"+ (i+1) +"</button>";
			}else{
				searchNav += "<button class='btn btn-outline-primary btn-sm go_jobs-search' data-id="+i+">"+ (i+1) +"</button>";
			}
		}
		if(numPages > boxLimit && (numPages - data.offset > boxLimit ) ){
			searchNav += "<button class='btn btn-outline-secondary btn-sm'>...</button>";
			searchNav += "<button class='btn btn-outline-primary btn-sm go_jobs-search' data-id="+ (numPages -1) +">"+numPages+"</a>";
		}

		end = (data.offset+1) * data.limit;
		if(end > data.total){ end = data.total; }

		var next = data.offset +1;
		if(next >= Math.ceil(data.total/data.limit)){ next = data.offset;}
		if( (numPages-1) > parseInt(next) ){
			searchNav += "<button class='btn btn-outline-primary btn-sm go_jobs-search' data-id="+next+">Next</a>";
		}else{
			searchNav += "<button class='btn btn-outline-secondary btn-sm' >Next</a>";
		}
	}
	searchNav += "</div>";
	searchNav += "<br>Showing "+ ((data.offset * data.limit) +1)  + " -> "+  end  +" of "+ data.total +" results";;
	$("#dataTable").append( searchNav);

	if(!isManager()){
		$(".mgr-only").hide();
	}

}
////////////////////////////////////////////////////// Schedules section ///////////////////////////////////////////////////
function drawSchedulesTable() {
        setupPage(" <a href='#' class='go_schedules'>Schedules</a>", "Scheduled Jobs");
        $("#dataTableDiv").append("<div id='dataTable'></div>");
        var row = `<table class='table table-sm table-bordered table-hover table-striped' id='schedule-table'>
                <thead><tr>
                        <th>Job Name</th>
                        <th>Managing A3</th>
                        <th>Type</th>
                        <th>Status</th>
                        <th>Schedule</th>
                        <th>Last Run</th>
                        <th>Next Run</th>
                        <th></th>
                </tr></thead><tbody></tbody>`;
	if(!checkNoA3s()){ return; }
	var a3s = JSON.parse(sessionStorage.getItem("a3Cache"));
	let a3Cnt = a3s.length;
        if(a3Cnt ==0){
                var row = "<div class='row bg-info m-2'><div class='col-12 panel-inactive rounded align-middle p-4' style='text-align:center;'><h3 >There are no Job Schedules setup yet.</h3><br><button class='btn btn-success go_sched-new'>Add a Job Schedule</button></div></div>";
                $("#dataTable").append(row);
                return;
        }
        $("#dataTable").append(row);
	var a3s = JSON.parse(sessionStorage.getItem("a3Cache"));
	$.each(a3s, function(i, a){
		wsCall("schedules",null,"populateSchedules",a.id, a.id);
	});

	$('#schedule-table').DataTable({
		pageLength: 20,
		lengthMenu: [5, 10,15, 20, 50, 100, 200, 500],
		language: {
		      lengthMenu: "Display _MENU_",
		}
		});
	adjustTableNav($("#schedule-table_wrapper"));
        var table = $('#schedule-table').DataTable();
	if(!isManager()){
		table.column( 1 ).visible( false );
	}
}

function populateSchedules(data, a3id){
        if(data.result != "success"){
                doToast(data.message, "Failed to get job schedules", "error");
                return;
        }
	let a3= getA3FromId(a3id);

	var table = $('#schedule-table').DataTable();
        $.each(data.Schedules, function(i, rowData){
		var result = [];
                rowData.scheduleType = Number(rowData.scheduleType);
		if(rowData.scheduleType == 5 && rowData.jobType ==1){ return; }
		if(rowData.jobType == 100 || rowData.jobType ==101){ return; }
		if(rowData.jobType == 200 || rowData.jobType ==201){ return; }
                jQuery.each(rowData.days, function(i,d){ rowData.days[i] = Number(d);   });
                var statusText = "Scheduling Enabled";
                if(rowData.state==0){ statusText = "<i>Scheduling Paused</i>"; }
                if(rowData.scheduleType ==4){ statusText = "<b>Manual</b>"; }
                var schedType = "";
                if(rowData.jobType ==0){
                        schedType = "Backup";
                }else if(rowData.jobType == 1){
                        schedType = "Full Restore";
                }else if(rowData.jobType == 5){
                        schedType = "Replication";
                }
		let col1 = `
			<span><i style='color:#eecc0b;' class='clickable fas fa-edit schedule-edit' data-type=${rowData.jobType} data-id=${rowData.scheduleID} data-site=${a3id} title='Edit Job'> </i> &nbsp;
			<span><i style='color:#42c200;' class='clickable fas fa-play-circle schedule-run-menu' data-id=${rowData.scheduleID} data-site=${a3id} title='Run Job Now'> </i> &nbsp; 
		 <span class='searchable clickable schedule-edit' data-type=${rowData.jobType} data-id=${rowData.scheduleID} data-site=${a3id}>${rowData.name} </span>`;
                let col2 =`<span data-id="${rowData.scheduleID}" data-site=${a3id} data-type=${rowData.jobType}> <span class='searchable'>${a3.name}</span></span>`;
                let col3 =`${getJobTypeIcon(rowData.jobType)}  ${schedType} `;
                let col4 = `${statusText}`; 
                let col5 =`${getScheduleTime(rowData)}`;
                var last = "N/A";
                if(rowData.lastran > 1 ){ last = getDate(rowData.lastran); }
                let col6 = `${last}`;
                var next = "N/A";
                if(isCheckedBool(rowData.state) && rowData.scheduleType !=4){
                        var nd = getSchedNext(rowData);
                        var next = 'Never';
                        if(nd != 'Never'){ next = getDate(nd.getTime()/1000); }
                }
                let col7 =`${next}`;
		let col8 =`<span><i class='clickable fas fa-trash schedule-delete-menu' data-id=${rowData.scheduleID} data-site=${a3id} title='Delete Scheduled Job'> </i>`;
		result.push(col1);
		result.push(col2);
		result.push(col3);
		result.push(col4);
		result.push(col5);
		result.push(col6);
		result.push(col7);
		result.push(col8);
		table.row.add(result);
        });
	table.draw();
        finishLineLoad();
}

function drawEditSchedule(data, a3id){
        setupPage(" <a href='#' class='.go_sched-edit' >Edit Schedule</a>", "Edit Schedule");
        $("#dataTableDiv").append("<div id='dataTable'></div>");
}

// 
function handleEditSchedule(data, a3id){
	if(data != null){ return drawEditSchedule(data,a3id); }

        setupPage(" <a href='#' class='.go_sched-new' >Add Schedule</a>", "Add Schedule");
        $("#dataTableDiv").append("<div id='dataTable'></div>");
	if(!checkNoA3s()){ return; }

	var a3s = JSON.parse(sessionStorage.getItem("a3Cache"));
	let a3Cnt = a3s.length;

	var a3pick = "";
	if(a3Cnt ==0){
		// handle no A3s yet
	}else if(a3Cnt ==1){
		a3pick = `<input type='hidden' id='a3id' value='${a3s[0].id}'>`;
	}else{
		a3pick += `<br><h5>Select Target A3</h5>
			<select class='custom-select' id='a3id'>`;
		$.each(a3s, function(i, a3){
			a3pick += `<option value='${a3.id}'>${a3.name}</option>`;
		});
		a3pick += `</select>`;
	}

        var row = `<div class='container'>

        <div class='col-4 mx-auto'>
		<div>
		<div class='p-2'><h3>Schedule New Job</h3></div>
		<div class='p-2'>${a3pick}</div>
		<div class='p-2'>
			<h4>Pick your job type:</h4>
			<button class='btn btn-flat btn-success sched-type-new' data-type='backup' >Backup</button>
			<button class='btn btn-flat btn-warning sched-type-new' data-type='restore' >Restore</button>
			<button class='btn btn-flat btn-primary sched-type-new' data-type='replicate' >Replicate</button>
		</div>
		<div><span id='sched-type-msg'></div>
		</div>

        </div><br><br>`;
	$("#dataTable").append(row);
}

// shared with all schedule types
function setSharedSched(dat, a3id){
	$("#a3id").val(a3id);
	$("#sched-a3id").val(a3id);
	$("#schedule-name").val(dat.name);
	$("#schedule-save").data("id", dat.ID);	// set schedID
	$("#sched-freq").val(dat.scheduleType).trigger('change');	
	if(dat.days.includes('0')){ $("#sun").prop('checked',true); }
	if(dat.days.includes('1')){ $("#mon").prop('checked',true); }
	if(dat.days.includes('2')){ $("#tue").prop('checked',true); }
	if(dat.days.includes('3')){ $("#wed").prop('checked',true); }
	if(dat.days.includes('4')){ $("#thu").prop('checked',true); }
	if(dat.days.includes('5')){ $("#fri").prop('checked',true); }
	if(dat.days.includes('6')){ $("#sat").prop('checked',true); }
	if(dat.numConcurrent){ $("#numConcurrent").val(dat.numConcurrent) }
	let emailLvl =0;
	if(dat.Options.allowJobEmail){ emailLvl =dat.Options.allowJobEmail; }
	$("#emailLevel").val(emailLvl).trigger('change');
	$("#sched-hour").val(dat.runHour);
	$("#sched-hour").val(new Date(dat.timestamp * 1000).getHours());
	$("#sched-min").val(dat.runMin);	
	$("#sched-min").val(new Date(dat.timestamp * 1000).getMinutes());
	$("#sched-interval").val(dat.interval);	
	$("#sched-monthday").val(new Date(dat.timestamp * 1000).getDate());
}
function populateGfsOpts(dat, ourGfs){
	if(dat.gfsProfiles.length==0){ return; }
	$("#gfsProfile").empty();
	$.each(dat.gfsProfiles, function(i,row){
		let sel = "";
		if(ourGfs == row.gfsId){ sel = "selected"; }
		$("#gfsProfile").append(`<option value=${row.gfsId} ${sel}>${row.name}</option>`);
	});

}

///////////////////////////////////////////////////// Restore schedules /////////////
function editRestSchedule(data, a3id){
        finishLineLoad();
        if(data.result != "success"){
                doToast(data.message, "Failed to get job details", "error");
                return;
        }
        let dat = data.Schedule;
        setupPage(" <a href='#' class='schedule-edit' data-site="+a3id+" data-id="+dat.ID+" data-type=1 >Edit "+dat.name+"</a>", "Edit "+dat.name);
        $("#dataTableDiv").append("<div id='dataTable'></div>");
        renderSchedulePanel();
        renderRestorePanel();
	if(dat.scheduleType ==5){
		$("#schedule-save").removeClass("btn-primary");
		$("#schedule-save").addClass("btn-success");
		$("#schedule-save").text("Run Now");
	}

        setSharedSched(dat, a3id);
	$("#sched-freq option[value='0']").remove();
	$("#sched-freq option[value='1']").remove();
	$('#sched-freq').append($('<option>', { value: 5, text: 'Run Immediately' }));
	$("#sched-freq").val(dat.scheduleType).trigger("change");


        if(dat.Options.boot){ $("#bootTarget").prop('checked',true); }
        let guy = $("#target-systems");
        let trash = `<i class='fas fa-trash rep-trash clickable float-right' ></i>`;
        $.each(dat.VMs, function(i, v){
                let destUuid = v.destHost.uuid;
                let destSr = "";
                let srNote ="";
                if(v.destSr){
                        destSr = v.destSr.uuid;
                        srNote = `[ ${v.destSr.name} ] `;
                }
                let destName = v.destHost.nativeName + " ["+ v.destHost.name+"]";
                let network = v.network;
                let netNote = "";
                if(network){ 
			netNote = `Network: ${network}`; 
		}
                if(v.destHost.type ==3){
                        srNote = "[ Default VHD Path ]";
                        if(v.destSr){ srNote = v.destSr; }
                        netNote = "";
                }
		let verText = getDate(v.version);
		if(v.version ==0){
			verText = "<span style='color:red;'>Backup Version no longer available!</span>";
		}
                let targ = `
                                <li data-id='${v.uuid}' data-ts=${v.timestamp} data-destuuid='${destUuid}' data-sr='${destSr}' data-network='${network}' class='sched-src-item'> 
			<span><b>${v.name}</b>  [${verText}]  <i class='fas fa-long-arrow-alt-right'></i> <b>${destName}</b>  ${srNote} <i>${netNote}</i> ${trash}</span>

                                </li>
                        `;
                guy.append(targ);
        });
	let args= new Object();
	args["backups"] =1;
        wsCall("sources",args, "populateRestSrc", null, a3id);
        let cache = sessionStorage.getItem("poolCache");
        cache = JSON.parse(cache);
        populateQuickRestoreItems(cache, a3id); // this sets the hosts, pools, & SR dropdowns
        wsCall("gfsProfiles",null, "populateGfsOpts", dat.gfsProfileAds, a3id);
}

function createNewRestore(a3id){
        setupPage(" <a href='#' class='go_sched-new' >New Restore</a>", "New Restore");
        $("#dataTableDiv").append("<div id='dataTable'></div>");
        renderSchedulePanel();
        renderRestorePanel();

	$("#sched-freq option[value='0']").remove();
	$("#sched-freq option[value='1']").remove();
	$('#sched-freq').append($('<option>', { value: 5, text: 'Run Immediately' }));
	$("#sched-freq").val(5).trigger("change");

        $("#a3id").val(a3id);
        $("#sched-a3id").val(a3id);
        $("#schedule-name").val("New Restore Job");
        $("#schedule-save").data("id", 0);      // set schedID
        $("#emailLevel").val(0).trigger('change');

        $("#sched-monthday").val( new Date().getDate());
        $("#sched-hour").val( new Date().getHours());
        $("#sched-min").val( new Date().getMinutes());

	let args= new Object();
	args["backups"] =1;
        wsCall("sources",args, "populateRestSrc", null, a3id);
        let cache = sessionStorage.getItem("poolCache");
        cache = JSON.parse(cache);
        populateQuickRestoreItems(cache, a3id); // this sets the hosts, pools, & SR dropdowns
}


function renderRestorePanel(){
        var vhdTip = printTip('If left blank, the default VHD path configured on your Hyper-V server will be used.<br><br>If specified, use <b>a local path relative to the Hyper-V host</b> (eg. D:\\Hyper-V\\VHDs)');
	let netTip = printTip("Select the network for your VM.  This network list is based on the host/pool selected above.");
        let pane = `
                <input type='hidden' id='jobType' value=1>
            <div class="card card-primary card-outline card-outline-tabs" style='width:98%'>
                <div class="card-header p-0 border-bottom-0">
                </div>
                <div class="card-body">
                <table width=100%>
                <tr><td valign=top style='padding-right:10px;'>
                        <div class='sched-select-container'>
                                <div class="form-group">
                                  <label>Select Sources</label>
                                  <select id='src-systems-rest' class="select2" data-placeholder="Choose your systems here..." style="width: 100%;">
                                        <option>Loading systems, please wait...</option>
                                        </select>
                                </div>

                        </div>

			<select class='form-control rounded-0' id='src-versions-rest' style='display:none;'></select>
                </td>
                <td width=50% valign=top>

                                <div class="form-group">
                                  <label>Select Destination</label>
        <table width=100%><tr><td class='text-nowrap' width=50>Choose Destination:</td><td>
        <select class='form-control rounded-0' id='dest-uuid' >
                <option value="#"> Loading options, please wait... </option>
        </select>
        </td></tr>
        <tr id='sr-row' style='display: none;'><td>Choose Storage</td><td>
        <select class='form-control rounded-0' id='dest-sr' ></select>
        </td></tr>
        <tr id='vhd-row' style='display: none;'><td>VHD Path ${vhdTip}</td><td>
        <input class='form-control rounded-0' id='dest-vhd' placeholder='<Override Default VHD Path>'></select>
        </td></tr>
        <!-- <tr id='template-row' style='display: none;'><td style="line-height:5px;">Select Template </td><td>
        <select class='form-control rounded-0' id='dest-template' ></select>
        </td></tr> -->
        <tr id='network-row' style='display: none;'><td style="line-height:5px;">Select Network ${netTip}</td><td>
        <select class='form-control rounded-0' id='dest-network' ></select>
        </td></tr>
        </table>

		</td>
                </tr>
                <tr><td colspan=2>
                        <div class='container-fluid'>
                                <div class='d-flex justify-content-center'>
                                        <h1><i class='fas fa-arrow-alt-circle-down clickable add-rest-src' data-toggle='tooltip' title='Click to Add System to Job'> </i></h1>
                                </div>
                        </div>
                        <div class='container-fluid'>
                                <div class='d-flex justify-content-center'>
                                <div class='sched-selector-base sched-replicate'><ul id='target-systems' class='sched-rep-target'> </ul></div>
                                </div>
                        </div>
                
                </td></tr>
                </div>
           </div>

                `;

        $("#sched-targets").append(pane);
        $('.select2').select2()

        $("#sched-opts").append(row);
        var row = `
                <br>
                <table width=95%><tr><td colspan=3><h4>Options</h4><hr></td></tr>
                <tr>
                        <td valign='top' width='33%'>
                                <h5>Job Options</h5>
                      <div class="form-group">
                        <div class='custom-control custom-checkbox '> 
                                <input class="custom-control-input custom-control-input-warning custom-control-input-outline" type="checkbox" id="bootTarget" >
                                <label class='custom-control-label' for='bootTarget' id='boot-target-lbl'>Boot target VM when complete. ${printTip("Requires caching  is disabled.")}</label> 
                        </div> 


                      </div>
                        </td>
                        <td  width='33%' valign='top'>
                        <h5>Processing Options</h5>
                      <div class="form-group"> 
                                  <input class="form-control-sm" type="number" id="numConcurrent" style='width: 50px;' value=1>
                                  <label>Concurrency ${printTip("Sets how many systems will be processed at the same time.  <br>Higher values may require more resources (memory, ABDs, etc)")}</label>


                        </div>
                        </td>

                        <td  width='33%' valign='top'>
                        <h5>Notification Options</h5>
                      <div class="form-group"> 
                                  <label for='emailLevel' id='emailLevelLbl'>Email only when errors occur</label><br>
                                  <input class="custom-range" type="range" id="emailLevel" style='width: 100px;' min="0" max="2" >
                        </div>
                        </td>
                </tr>
                <tr><td colspan=3>
                <div class='float-right'><button class='btn btn-primary btn-lg' id='schedule-save' data-id=0>Save</button></div>
                </td></tr>
                </table>
                `;
        $("#sched-opts").append(row);

        $("#cbt").trigger('click');
        renderTips();
}
function populateRestSrc(data){
        let guy = $("#src-systems-rest");
        guy.empty();
        $.each(data.sources, function(i, vm){ guy.append(`<option value='${vm.uuid}'>${vm.name}</option>`); });
}
function populateRestVersions(data){
        let guy = $("#src-versions-rest");
        guy.empty();
        $.each(data.versions, function(i, v){ 
		let ods = "";
		if(v.isOffsite==1 && v.isOnsite ==0){ ods = " [ *Offsite Version* ]"; }
		guy.append(`<option value='${v.timestamp}'>${getDate(v.timestamp)}  [${prettySize(v.totalSize)}]  ${ods}</option>`); 
	});
	if(!data.versions){
		guy.append(`<option value='0'>No backups found for system.</option>`);
	}

}


///////////////////////////////////////////////////// Replication schedules /////////////
function editRepSchedule(data, a3id){
	finishLineLoad();
	if(data.result != "success"){
		doToast(data.message, "Failed to get job details", "error");
		return;	
	}
	let dat = data.Schedule;
        setupPage(" <a href='#' class='schedule-edit' data-site="+a3id+" data-id="+dat.ID+" data-type=5 >Edit "+dat.name+"</a>", "Edit "+dat.name);
        $("#dataTableDiv").append("<div id='dataTable'></div>");
	renderSchedulePanel();
	renderReplicatePanel();

	setSharedSched(dat, a3id);


	if(dat.Options.doCBT){ $("#cbt").prop('checked',true).trigger('change'); }
	else if(dat.Options.QHB){ $("#qhb").prop('checked',true).trigger('change'); }
	else { $("#enhanced").prop('checked',true).trigger('change'); }
	if(dat.Options.snapshotTarget){ $("#snapReplica").prop('checked',true); }
	if(dat.Options.restore){ $("#preserveMAC").prop('checked',true); }
	if(dat.Options.boot){ $("#bootTarget").prop('checked',true); }
	if(dat.Options.force ==0){ $("#enableCache").prop('checked',true); }
	if(dat.Options.forceFull){ $("#forceFull").prop('checked',true); }
	if(dat.Options.vaultEnable){ $("#enable-vault").prop('checked',true); }
	if(dat.gfsProfileAds){
		$("#use-gfs").prop('checked',true).trigger('click'); 
		$("#gfsProfile").val(dat.gfsProfileAds);
	}
	let guy = $("#target-systems");
	let trash = `<i class='fas fa-trash rep-trash clickable float-right' ></i>`;
	$.each(dat.VMs, function(i, v){
		let destUuid = v.destHost.uuid;
		let destSr = "";
		let srNote ="";
		if(v.destSr){ 
			destSr = v.destSr.uuid; 
                        srNote = `[ ${v.destSr.name} ] `;
		}
		let destName = v.destHost.nativeName + " ["+ v.destHost.name+"]";
		let network = v.network;
		let netNote = "";
		if(network){ 
			netNote = `Network: ${network}`; 
		}
		if(v.destHost.type ==3){
			srNote = "[ Default VHD Path ]";
			if(v.destSr){ srNote = v.destSr; }
			netNote = "";
		}

                let targ = `
                                <li data-id='${v.uuid}' data-destuuid='${destUuid}' data-sr='${destSr}' data-network='${network}' class='sched-src-item'> 
                                        <span><b>${v.VMName}</b>  <i class='fas fa-long-arrow-alt-right' style='color:#6d98bf'></i> ${destName}  ${srNote} <i>${netNote}</i> ${trash}</span>
                                </li>
                        `;
                guy.append(targ);
	});

	wsCall("sources",null, "populateRepSrc", null, a3id);
	let cache = sessionStorage.getItem("poolCache");
	cache = JSON.parse(cache);
	populateQuickRestoreItems(cache, a3id);	// this sets the hosts, pools, & SR dropdowns
	wsCall("gfsProfiles",null, "populateGfsOpts", dat.gfsProfileAds, a3id);
}

function createNewReplicate(a3id){
        setupPage(" <a href='#' class='go_sched-new' >New Repliate</a>", "New Repliate");
        $("#dataTableDiv").append("<div id='dataTable'></div>");
	renderSchedulePanel();
	renderReplicatePanel();

	$("#a3id").val(a3id);
	$("#sched-a3id").val(a3id);
	$("#schedule-name").val("New Replication Job");
	$("#schedule-save").data("id", 0);	// set schedID
	$("#emailLevel").val(0).trigger('change');

	$("#mon").prop('checked',true)
	$("#tue").prop('checked',true)
	$("#wed").prop('checked',true)
	$("#thu").prop('checked',true)
	$("#fri").prop('checked',true)

	$("#sched-monthday").val( new Date().getDate());
	$("#sched-hour").val( new Date().getHours());
	$("#sched-min").val( new Date().getMinutes());

	wsCall("sources",null, "populateRepSrc", null, a3id);
	let cache = sessionStorage.getItem("poolCache");
	cache = JSON.parse(cache);
	populateQuickRestoreItems(cache, a3id);	// this sets the hosts, pools, & SR dropdowns
	wsCall("gfsProfiles",null, "populateGfsOpts", 0, a3id);
}
function renderReplicatePanel(){
	var vhdTip = printTip('If left blank, the default VHD path configured on your Hyper-V server will be used.<br><br>If specified, use <b>a local path relative to the Hyper-V host</b> (eg. D:\\Hyper-V\\VHDs)');

//	let tempTip = printTip("Select the most suitable template for your VM.  UEFI based VMs <b>must</b> select the proper UEFI based template to boot properly.  Most MBR based systems can safely choose 'Other install media', but please read our related KB article if you are unsure what to use.");
	let netTip = printTip("Select the network for your VM.  This network list is based on the host/pool selected above.");

	let pane = `
		<input type='hidden' id='jobType' value=5>
            <div class="card card-primary card-outline card-outline-tabs" style='width:95%'>
		<div class="card-header p-0 border-bottom-0">
		</div>
		<div class="card-body">
		<table width=95%>
		<tr><td valign=top style='padding-right:10px;'>
			<div class='sched-select-container'>
				<div class="form-group">
				  <label>Select Sources</label>
				  <select id='src-systems' class="select2" multiple="multiple" data-placeholder="Choose your systems here..." style="width: 100%;">
					<option>Loading systems, please wait...</option>
					</select>
				</div>

			</div>
		</td>
		<td width=50% valign=top>

				<div class="form-group">
				  <label>Select Destination</label>
	<table width=100%><tr><td class='text-nowrap' width=50>Choose Destination:</td><td>
	<select class='form-control rounded-0' id='dest-uuid' >
		<option value="#"> Loading options, please wait... </option>
	</select>
	</td></tr>
	<tr id='sr-row' style='display: none;'><td>Choose Storage</td><td>
	<select class='form-control rounded-0' id='dest-sr' ></select>
	</td></tr>
	<tr id='vhd-row' style='display: none;'><td>VHD Path ${vhdTip}</td><td>
	<input class='form-control rounded-0' id='dest-vhd' placeholder='<Override Default VHD Path>'></select>
	</td></tr>
	<!-- <tr id='template-row' style='display: none;'><td style="line-height:5px;">Select Template </td><td>
	<select class='form-control rounded-0' id='dest-template' ></select>
	</td></tr> -->
        <tr id='network-row' style='display: none;'><td style="line-height:5px;">Select Network ${netTip}</td><td>
        <select class='form-control rounded-0' id='dest-network' ></select>
        </td></tr>
	</table>

		</td>
		</tr>
		<tr><td colspan=2>
			<div class='container-fluid'>
				<div class='d-flex justify-content-center'>
					<h1><i class='fas fa-arrow-alt-circle-down clickable add-rep-src' data-toggle='tooltip' title='Click to Add System to Job'> </i></h1>
				</div>
			</div>
			<div class='container-fluid'>
				<div class='d-flex justify-content-center'>
				<div class='sched-selector-base sched-replicate'><ul id='target-systems' class='sched-rep-target'> </ul></div>
				</div>
			</div>
		
		</td></tr>
		</div>
	   </div>

		`;

	$("#sched-targets").append(pane);
	$('.select2').select2()

        $("#sched-opts").append(row);
        var row = `
                <br>
                <table width=95%><tr><td colspan=3><h4>Options</h4><hr></td></tr>
                <tr>
                        <td valign='top' width='33%'>
                                <h5>Job Method</h5>
                            <div class="form-group"> 
                                <div class="custom-control custom-radio">
                                  <input class="custom-control-input sched-processor-rep" type="radio" id="cbt" name="processor">
                                  <label for="cbt" class="custom-control-label">CBT Based</label>
                                </div>
                                <div class="custom-control custom-radio">
                                  <input class="custom-control-input sched-processor-rep" type="radio" id="enhanced" name="processor" >
                                  <label for="enhanced" class="custom-control-label">Enhanced (agentless)</label>
                                </div>
                                <div class="custom-control custom-radio">
                                  <input class="custom-control-input sched-processor-rep" type="radio" id="qhb" name="processor" >
                                  <label for="qhb" class="custom-control-label">Q-Hybrid (requires QS Agent)</label>
                                </div>
                            </div>

                        <h5>Advanced Options</h5>
                      <div class="form-group">
<!--                        <div class="custom-control custom-checkbox">
                          <input class="custom-control-input custom-control-input-warning custom-control-input-outline" type="checkbox" id="deleteBackup" >
                          <label for="deleteBackup" class="custom-control-label">Delete backup after replication ${printTip("Delete the backup version used to create the replicated VM.<br>Note: Not applicable with CBT repliation.")}</label>
                        </div> -->
                        <div class="custom-control custom-checkbox">
                          <input class="custom-control-input custom-control-input-primary custom-control-input-outline" type="checkbox" id="snapReplica"  >
                          <label for="snapReplica" class="custom-control-label ">Snapshot the Replica VM (target) prior to job  ${printTip("This option tells Alike to take a snapshot of the target/replica prior to running the job.  If any errors occur during the replication, Alike will attempt to revert to this snapshot.<br>Upon successful completion of the job, this snapshot will be removed.<br>Note: requires additional storage in the target SR for the duratio of the job.")}</label>
                        </div>
                        <div class="custom-control custom-checkbox">
                          <input class="custom-control-input custom-control-input-warning custom-control-input-outline" type="checkbox" id="preserveMAC" >
                          <label for="preserveMAC" class="custom-control-label">Preserve source VM's MAC address  ${printTip("The replicted VM will be assigned the same MAC address as the source VM.<br>Note: this can cause issues if both systems are on the same LAN.  Use only when necessary.")}</label>
                        </div>
			<div class='custom-control custom-checkbox '> 
				<input class="custom-control-input custom-control-input-warning custom-control-input-outline" type="checkbox" id="bootTarget" >
				<label class='custom-control-label' for='bootTarget' id='boot-target-lbl'>Boot target VM when complete. ${printTip("Requires caching  is disabled.")}</label> 
			</div> 

                          <div class='custom-control custom-checkbox custom-switch-off-warning custom-switch-on-primary'> 
                          <input class="custom-control-input custom-control-input-warning custom-control-input-outline" type="checkbox" id="enableCache" >
                                        <label class='custom-control-label' for='enableCache' id='disable-cache-lbl'>Enable State cache ${printTip("<b>Warning</b> Enabling caching can greatly improve repeat replication change detection times, but the target VM <u>must not be modified or booted</u> between runs.")}</label> 
                          </div> 


                      </div>
                        </td>
                        <td  width='33%' valign='top'>
                        <h5>Processing Options</h5>
                      <div class="form-group"> 
                                  <input class="form-control-sm" type="number" id="numConcurrent" style='width: 50px;' value=1>
                                  <label>Concurrency ${printTip("Sets how many systems will be processed at the same time.  <br>Higher values may require more resources (memory, ABDs, etc)")}</label>


                                <div class='custom-control custom-switch custom-switch-off-primary custom-switch-on-success'> 
                                        <input type='checkbox' class='custom-control-input' id='use-gfs'> 
                                        <label class='custom-control-label' for='use-gfs' id='use-gfs-lbl'>Use Standard Retention</label> 
                                </div> 
                                <div class='nuttin' style='display:none;' id='gfsProfileList'>
                                Selected GFS Profile: <select id='gfsProfile' class='form-control rounded-0'>
                                        <option value=0>No GFS Profiles Defined!</option>
                                        </select>
                                </div>
                        </div>
                        </td>

                        <td  width='33%' valign='top'>
                        <h5>Notification Options</h5>
                      <div class="form-group"> 
                                  <label for='emailLevel' id='emailLevelLbl'>Email only when errors occur</label><br>
                                  <input class="custom-range" type="range" id="emailLevel" style='width: 100px;' min="0" max="2" >
                        </div>
                        </td>
                </tr>
                <tr><td colspan=3>
                <div class='float-right'><button class='btn btn-primary btn-lg' id='schedule-save' data-id=0>Save</button></div>
                </td></tr>
                </table>
                `;
        $("#sched-opts").append(row);

	$("#cbt").trigger('click');
	renderTips();
}
function populateRepSrc(data){
	let guy = $("#src-systems");
	guy.empty();
	$.each(data.sources, function(i, vm){ guy.append(`<option value='${vm.uuid}'>${vm.name}</option>`); });
}

////////////////////////////////////////////////////////////// Backup Schedules //////////////////////////////////////

function createNewBackup(a3id){
        setupPage(" <a href='#' class='go_sched-new' >New Backup</a>", "New Backup");
        $("#dataTableDiv").append("<div id='dataTable'></div>");
	renderSchedulePanel();
	renderBackupPanel();

	$("#a3id").val(a3id);
	$("#sched-a3id").val(a3id);
	$("#schedule-name").val("New Backup Job");
	$("#schedule-save").data("id", 0);	// set schedID
	$("#emailLevel").val(0).trigger('change');

	$("#mon").prop('checked',true)
	$("#tue").prop('checked',true)
	$("#wed").prop('checked',true)
	$("#thu").prop('checked',true)
	$("#fri").prop('checked',true)

	$("#sched-monthday").val( new Date().getDate());
	$("#sched-hour").val( new Date().getHours());
	$("#sched-min").val( new Date().getMinutes());
	wsCall("sources",null, "populateBackupSrc", null, a3id);
	wsCall("gfsProfiles",null, "populateGfsOpts", 0, a3id);
	renderTips();
}


function editBackupSchedule(data, a3id){
	finishLineLoad();
	let dat = data.Schedule;

	// check policytype: 0 manual selection, 1= host, 2=keyword, 3=tag
	// 
	// TODO: Make backup options disabled when changing qhb/enh

        setupPage(" <a href='#' class='schedule-edit' data-site="+a3id+" data-id="+dat.ID+" data-type=0 >Edit "+dat.name+"</a>", "Edit "+dat.name);
        $("#dataTableDiv").append("<div id='dataTable'></div>");
	renderSchedulePanel();
	renderBackupPanel();

	setSharedSched(dat, a3id);

	if(dat.useEnhanced){ $("#enhanced").prop('checked',true).trigger('click'); }
	if(dat.Options.doCBT){ $("#cbt").prop('checked',true); }
	if(dat.Options.policyFilterString.length > 0){ $("#keyword").val(dat.Options.policyFilterString); }	// switch tabs somehow
	if(dat.Options.forceFull){ $("#forceFull").prop('checked',true); }
	if(dat.Options.vaultEnable){ $("#enable-vault").prop('checked',true); }
	if(dat.gfsProfileAds){
		$("#use-gfs").prop('checked',true).trigger('change'); 
		$("#gfsProfile").val(dat.gfsProfileAds);
	}
	if(dat.Options.QHB==1){ $("#qhb").prop('checked', true); }
	if(dat.diskandmemory){ $("#diskMem").prop('checked',true); }
	
	$("#sched-keyword").val(dat.Options.policyFilterString);
	if (dat.Options.policyType ==3){ $("#useTags").prop("checked",true); }
	if (dat.Options.policyType >= 2){
		$("#keyword-tab").trigger('click');
		$("#wholehost-tab").addClass("disabled");
		$("#manual-tab").addClass("disabled");
	}else if (dat.Options.policyType == 1){
		$("#wholehost-tab").trigger('click');
		let guy = $("#target-hosts");
		$.each(dat.Hosts, function(i, h){
			let sys = $(makeSourceHost(h.uuid, h.nativeName, h.type ,-1));
			guy.append(sys);
		});
		$("#keyword-tab").addClass("disabled");
		$("#manual-tab").addClass("disabled");
	}

	let guy = $("#target-systems");
	$.each(dat.VMs, function(i, v){
		// Disks has all of their disks.  volumes is the exclusion info.
		// VMName, UUID, disks[name, uuid, size, position]
		let sys = $(makeSourceItem(v.UUID, v.name ,-1, v.type));
		sys.append(`<div class='disk-list' style='display:none;'><b>Selected Disks</b><br><div>`);
		let targ = $(sys).find(".disk-list");
		$(sys).find(".sched-trash").show();
		$.each(v.Disks, function(i, d){
			let chk = true;
			// sometimes they're an int, sometimes a string.  awesome work guys.
			if(v.volumes.length > 0 && (!v.volumes.includes(i) && !v.volumes.includes(String(i)) ) ){ chk = false; }
			targ.append(makeDiskItem(d.name, i, d.size, chk));
		});
		guy.append(sys);
		var args= new Object();
		args["vmuuid"] = v.UUID;
		sys.find(".sched-disk-expand").show();
	});

	wsCall("sources",null, "populateBackupSrc", null, a3id);
	wsCall("gfsProfiles",null, "populateGfsOpts", dat.gfsProfileAds, a3id);
	renderTips();
}

function renderSchedulePanel(){
	// archor for various WS calls
	let tag = `
		<input type='hidden' id='sched-a3id' value=0 >
		`;
	$("#dataTable").append(tag);
	
	let schedID=0;

	let nameBox = `<input class='form-control form-control-lg' type='text' placeholder='Set your job name here' id='schedule-name'>`;
        var row = `<div class='container-fluid'>
		<div class='col-12'>
			<div>
			<div class='col-4 mx-auto p-2'><h3>${nameBox}</h3></div>

			<div class='col-10 mx-auto p-2' id='sched-times'> </div>
			<div class='col-10 mx-auto p-2' id='sched-targets'> </div>
			<div class='col-10 mx-auto p-2' id='sched-opts'> </div>
			<div class='col-10 mx-auto p-2' id='sched-buttons'> </div>
			</div>
		</div>`;
	$("#dataTable").append(row);

	var row = `
		<table width=95%><tr><td colspan=2><h4>Schedule</h4><hr></td></tr>
		<td width='475px;'>

                    <label>Schedule Type:</label>
			<select id='sched-freq' class='custom-select'>
			<option value=0>Daily</option>
			<option value=1>Monthly</option>
			<option value=4>Run Manually</option>
			</select>
		</td>
		<td>
			<span id='timepicker-panel'>
			    <label>Start time:</label>
			    <div class="input-group date" id="timepicker" data-target-input="nearest">
				      <input type="number" class="form-control" id='sched-hour' min=0 max=23 style='width:75px;'>
				      <input type="number" class="form-control" id='sched-min' min=0 max=59 style='width:75px;'>
				      <div class="input-group-append" data-target="#timepicker" data-toggle="datetimepicker">
					  <div class="input-group-text"><i class="far fa-clock"></i></div>
				      </div>
			    </div>
			</span>
		</td>
		</tr><tr>
		<td>
			<div id='weekdayspicker' class="weekdays-picker p-2">
				<input type="checkbox" id="mon" class="weekday" > <label for="mon">Mon</label>
				<input type="checkbox" id="tue" class="weekday" > <label for="tue">Tue</label>
				<input type="checkbox" id="wed" class="weekday" > <label for="wed">Wed</label>
				<input type="checkbox" id="thu" class="weekday" > <label for="thu">Thu</label>
				<input type="checkbox" id="fri" class="weekday" > <label for="fri">Fri</label>
				<input type="checkbox" id="sat" class="weekday" > <label for="sat">Sat</label>
				<input type="checkbox" id="sun" class="weekday" > <label for="sun">Sun</label>
			</div>
			<span id='dayofmonth' style='display:none;'>
				  <label>Day of Month:</label>
<!--				    <div class="input-group date" id="reservationdate" data-target-input="nearest">
					<input type="month" class="form-control datetimepicker-input" data-target="#reservationdate"/>
					<div class="input-group-append" data-target="#reservationdate" data-toggle="datetimepicker">
					    <div class="input-group-text"><i class="fa fa-calendar"></i></div>
					</div>
				    </div> -->
				<div class='input-group'><input id='sched-monthday' type='number' min=1 max=31 style='width:45px;'> </div>
			</span>
		</td><td>
			<span id='sched-intervals'>
			<label>Repeats</label>
			<select id='sched-interval' class='custom-select'>
				<option value=0>Once per day</option>
				<option value=900>Every 15 Mins</option>
				<option value=1800>Every 30 Mins</option>
				<option value=3600>Every 1 Hour</option>
				<option value=7200>Every 2 Hours</option>
				<option value=14400>Every 4 Hours</option>
				<option value=21600>Every 6 Hours</option>
				<option value=28800>Every 8 Hours</option>
				<option value=43200>Every 12 Hours</option>
			</select>
			</span>
		</td></tr>
		`;
	$("#sched-times").append(row);
}

function renderBackupPanel(){
	var row = `
		<!-- <div style='width:95%;'>
		<br><h4>Job Targets</h4></div> -->
		<input type='hidden' id='jobType' value=0>

            <div class="card card-primary card-outline card-outline-tabs" style='width:95%'>
              <div class="card-header p-0 border-bottom-0">
                <ul class="nav nav-tabs" id="source-select" role="tablist">
                  <li class="nav-item"> <a class="nav-link sched-tabber active" id="manual-tab" data-toggle="tab" href="#manual" role="tab" data-target="manual">Manual Selection</a> </li>
                  <li class="nav-item"> <a class="nav-link sched-tabber" id="keyword-tab" data-toggle="tab" href="#keyword" role="tab" data-target="keyword">Keyword Selection</a> </li>
                  <li class="nav-item"> <a class="nav-link sched-tabber" id="wholehost-tab" data-toggle="tab" href="#wholehost" role="tab" data-target="wholehost">Whole Host</a> </li>
                </ul>
              </div>
		<div class="card-body">
			<div class="tab-content" id="source-selectContent">
	<!-- Begin tab 1 -->
			<div class="tab-pane fade show active sched-tab-content" id="manual" role="tabpanel" aria-labelledby="manual-tab">
		<table width=95%>
		<tr><td width='50%'>
				<div class='input-group' style='width:96%;'>
					<div class='input-group-append'><span class='input-group-text'><i class='fas fa-search'></i></span></div>
					<input type='text' id='source-filter' class='form-control sched-selectable' placeholder='Filter by name or Guid'>
				</div>
		</td><td>
				<div class='input-group ' style='width:100%; padding-left: 5%;'><h4>Systems to protect</h4> </div>
		</td></tr>
		<tr><td colspan=2>
			<div class='sched-select-container'>
				<div class='sched-selector-base sched-selectable'><ul id='src-systems'></ul></div>
				<div class='sched-selector-base sched-selected'><ul id='target-systems'> </ul></div>
			</div>
		</td></tr>
		</table>
			</div>
	<!-- End tab 1 -->
		<div class="tab-pane fade  sched-tab-content" id="keyword" role="tabpanel" aria-labelledby="keyword-tab">
		
                <table width=95%>
                <tr><td width='50%'>
                                <div class='input-group' style='width:96%;'>
                                        <div class='input-group-append'><span class='input-group-text'><i class='fas fa-search clickable keyword-test'></i></span></div>
                                        <input type='text' id='sched-keyword' class='form-control sched-selectable' placeholder='Enter a keyword to match systems at runtime'>
                                </div>
                </td><td>
                                <div class='input-group' style='width:100%; padding-left: 5%;'><h4>Example results</h4> </div>
                </td></tr>
                <tr><td valign='top'>
			<input type='checkbox' id='useTags'> Use XenServer tags instead of Name (case sensitive)
			</td><td>
                        <div class='sched-select-container'>
                                <div class='sched-selector-base ' style='width:100%;'><ul id='target-systems-test'> </ul></div>
                        </div>
                </td></tr>
                </table>


		</div>
		<!-- whole host tab -->
		<div class="tab-pane fade  sched-tab-content" id="wholehost" name="wholehost" role="tabpanel" aria-labelledby="wholehost-tab">

		<table width=95%>
		<tr><td colspan=2>
			<div class='sched-select-container'>
				<div class='sched-selector-base sched-selectable'><ul id='src-hosts'></ul></div>
				<div class='sched-selector-base sched-selected'><ul id='target-hosts'> </ul></div>
			</div>
		</td></tr>
		</table>
		</div>

	<!-- End tab content -->
	</div>
</div>
</div>

		`;

	$("#sched-opts").append(row);
	var row = `
		<br>
		<table width=95%><tr><td colspan=3><h4>Options</h4><hr></td></tr>
		<tr>
			<td valign='top' width='33%'>
				<h5>Protection Method</h5>
			    <div class="form-group"> 
				<div class="custom-control custom-radio">
				  <input class="custom-control-input sched-processor" type="radio" id="enhanced" name="processor" checked>
				  <label for="enhanced" class="custom-control-label">Enhanced (agentless)</label>
				</div>
				<div class="custom-control custom-radio">
				  <input class="custom-control-input sched-processor" type="radio" id="qhb" name="processor" >
				  <label for="qhb" class="custom-control-label">Q-Hybrid (requires QS Agent)</label>
				</div>
			    </div>

			<h5>Advanced Options</h5>
                      <div class="form-group">
                        <div class="custom-control custom-checkbox">
                          <input class="custom-control-input" type="checkbox" id="cbt" value="option1" >
                          <label for="cbt" class="custom-control-label">Enable CBT</label>
                        </div>

                        <div class="custom-control custom-checkbox">
                          <input class="custom-control-input custom-control-input-warning custom-control-input-outline" type="checkbox" id="diskMem" >
                          <label for="diskMem" class="custom-control-label">Protect disk & memory ${printTip("For Xen and Enhanced only. <br>Can take extra time and storage to protect, and should only be enabled when specifically necessary.")}</label>
                        </div>
                        <div class="custom-control custom-checkbox">
                          <input class="custom-control-input custom-control-input-danger custom-control-input-outline" type="checkbox" id="forceFull" >
                          <label for="forceFull" class="custom-control-label">Always perform a full/baseline ${printTip("Bypass all optimizations and deltas, ensuring a full backup is taken.<br>Only needed in troubleshooting situations, enable with caution.")}</label>
                        </div>
                      </div>
			</td>
			<td  width='33%' valign='top'>
			<h5>Processing Options</h5>
                      <div class="form-group"> 
				  <input class="form-control-sm" type="number" id="numConcurrent" style='width: 50px;' value=1>
				  <label>Concurrency ${printTip("Sets how many systems will be processed at the same time.  <br>Higher values may require more resources (memory, ABDs, etc)")}</label>

				<div class='custom-control custom-switch custom-switch-on-success'> 
					<input type='checkbox' class='custom-control-input' id='enable-vault'> 
					<label class='custom-control-label' for='enable-vault' id='enable-vault-lbl'>Enable Offsite Vaulting</label> 
				</div> 

				<div class='custom-control custom-switch custom-switch-off-primary custom-switch-on-success'> 
					<input type='checkbox' class='custom-control-input' id='use-gfs'> 
					<label class='custom-control-label' for='use-gfs' id='use-gfs-lbl'>Use Standard Retention</label> 
				</div> 
				<div class='nuttin' style='display:none;' id='gfsProfileList'>
				Selected GFS Profile: <select id='gfsProfile' class='form-control rounded-0'>
					<option value=0>No GFS Profiles Defined!</option>
					</select>
				</div>
			</div>
			</td>

			<td  width='33%' valign='top'>
			<h5>Notification Options</h5>
                      <div class="form-group"> 
				  <label for='emailLevel' id='emailLevelLbl'>Email only when errors occur</label><br>
				  <input class="custom-range" type="range" id="emailLevel" style='width: 100px;' min="0" max="2" >
			</div>
			</td>
		</tr>
		<tr><td colspan=3>
		<div class='float-right'><button class='btn btn-primary btn-lg' id='schedule-save' data-id=0>Save</button></div>
		</td></tr>
		</table>
		`;
	$("#sched-opts").append(row);

	$("#src-systems").append(`<li class='sched-src-item' > Please wait, loading source systems...</li>`);
}

function populateBackupSrc(data){
	let guy = $("#src-systems");
	guy.empty();
	$.each(data.sources, function(i, vm){
		guy.append(makeSourceItem(vm.uuid, vm.name ,i, vm.isHidden));
	});
}
function makeSourceItem(uuid, name, pos, virt){
	let icon = getVirtTypeIcon(virt);
	let trash = "<i class='fas fa-trash sched-trash' style='display: none;'></i>";
	let it =`<li class='sched-src-item' data-pos=${pos} data-uuid='${uuid}'>${icon} ${name} <div class='sched-disk-expand' style='display: none;'> &nbsp; </div>${trash}</li>`;
	return it;
}
function populateDiskList(data, guy){
	let dlist = guy.find(".disk-list");
	if(dlist.length == 0){
		guy.append(`<div class='disk-list' style='display:none;'><b>Selected Disks</b><br><div>`);
		dlist = guy.find(".disk-list");
	}
	if(!data.Disks || data.Disks.length==0){
		dlist.addClass("bg-danger");
		dlist.append(`<span >NO Disks found! </span><br>`);
		return;
	}
	$.each(data.Disks, function(i, d){
		let d2 = makeDiskItem(d.name, i, d.size, true) ;
		dlist.append(d2);
	});
}
function makeDiskItem(name, pos, size, chk){
	let check = `checked`;
	if(!chk){ check = ""; }
	let d = `<input type='checkbox' data-id=${pos} style='margin-right:5px;' ${check}> <i class="far fa-hdd fa-sm"></i> &nbsp;<span title='${name}'>Disk ${(pos+1)} [${prettySize(size)}] - ${name} </span><br>`;
	return d;
}

function keywordTestResults(data, id){
	let guy = $("#target-systems-test");
	guy.empty();
	$.each(data.sources, function(i, v){
		let it =`<li class='sched-src-item' > ${v.name} </li>`;
		guy.append(it);
	});
	if(data.sources.length==0){
		let it =`<li class='sched-src-item' > No matches found! </li>`;
		guy.append(it);
	}

}
function populateSchedHosts(data){
	let guy = $("#src-hosts");
	let trg = $("#target-hosts");
	guy.empty();
	$.each(data.sources, function(i, host){
		if(trg.find(`[data-uuid="${host.uuid}"]`).length ==0){
			let it = makeSourceHost(host.uuid, host.hostName, host.virtType, i);
			guy.append(it);
		}
	});
	trg.find('.sched-trash').show();
}
function makeSourceHost(uuid, name, virtType, pos){
	let trash = "<i class='fas fa-trash sched-trash sched-host-trash' style='display: none;'></i>";
	let icon = getVirtTypeIcon(virtType);
	let it =$(`<li class='sched-src-host' data-pos=${pos} data-uuid='${uuid}'>${icon}  ${name} ${trash}</li>`);
	return it;
}

////////////////////////////////////////////////////// End Schedules section ///////////////////////////////////////////////////

function drawAllSystemsTable(data, showType) {
	setupPage(" <a href='#' class='go_vms'>All Systems</a>", "All Systems");
        $("#dataTableDiv").append("<div id='dataTable'></div>");

        var row = `<table class='table table-bordered table-hover table-striped' id='vm-table' style='width:100%'>
			<thead><tr>
			<th style='width: 15px;'>Type</th>
			<th>System Name</th>
			<th>A3 Owner</th>
			<th>Location</th>
			<th>Protection Options</th>
			<th>Backups</th>
			<th>Total Size</th>
			</tr></thead><tbody>`;

	if(!checkNoA3s()){ return; }

        if(data.VMs.length ==0){
                var row = "<div class='row bg-info m-2'><div class='col-12 panel-inactive rounded align-middle p-4' style='text-align:center;'><h3 >No Systems could be found.</h3><br><button class='btn btn-success go_hosts'>Add a Host?</button></div></div>";
                $("#dataTable").append(row);
                return;
        }

        jQuery.each(data.VMs, function(i,r){
                row += "<tr />";
                var agent = "Enhanced";
                // we have stored data from the agent
                if(r.authProfile !== null && r.authProfile != ""){
                         try{
                                var ag =JSON.parse(r.authProfile);
                                let tmp = ag.version.split(" ")[0]; // +" [seen: "+ timeSince(ag.lastCheck )+"]";
				if(r.type ==10){
					agent = `Full QS Agent [${tmp}]`;
				}else{
					agent += `, QHB  [${tmp}]`;
				}
                        } catch(err){ agent="";}
                }else if(r.hostUUID == null){
			agent = "Could not parse agent info";
		}

                var head = "<td>" + getVirtTypeIcon(r.type) + "</td>";
                var guid = r.uuid;
                if(r.guid){ guid = r.guid; }

		let found=true;
		let loc = ``;
		if(r.hostUUID ==""  && r.type !=10){
			loc = "Location unknown";
			agent = '';
			found=false;
                }else if(r.type ==2 || r.type ==22){
                        loc = getVirtTypeIcon(r.type)+` <span data-toggle='tooltip' title='Pool: ${getNameFromCache(r.poolID)}'>${getNameFromCache(r.hostUUID)} </span> `;
                }else if(r.type ==3){
                        loc = getVirtTypeIcon(r.type)+ " "+ getNameFromCache(r.hostUUID);
		}else if(r.type ==10){
			loc = `Managed by ${r.a3Name}`;
		}else{
			loc = `N/A`;
		}

                head += `<td class='clickable view-vm-details' data-uuid='${guid}' data-site=${r.a3id}>${getPowerState(r.powerState)} ${r.name}</td>
			<td class='clickable view-a3' data-id='${r.a3id}'> ${r.a3Name} </td>
			<td class='clickable' data-id='${r.a3id}'> ${loc} </td>
			<td class='clickable view-vm-details' data-uuid='${guid}' data-site=${r.a3id}> ${agent} </td>`;
                var lepoch = 0;
                var ts;
                var lc = " ";
                    lc = "jld_stat_active";
                        var ts = "No backups found";
			if(r.lastJobResult > 0){
				ts = r.lastJobResult +" Backups";
			}
               head += "<td >" + ts + "</td>";
                var sz = prettySize(r.totalSize);
                if(r.totalSize == "0"){ sz = "N/A"; }
		if(!found){ sz = "-"; }
                head += "<td class='hideable-column'> "+ sz+"</td>";
                head += "</tr>";
                row += head;
        });
        $("#dataTableDiv").append( row);
	$("#vm-table").DataTable({
		pageLength: 20,
		lengthMenu: [5, 10,15, 20, 50, 100, 200, 500],
		language: {
		      lengthMenu: "Display _MENU_",
		},
		buttons: [
		    {
			extend:    'copyHtml5',
			className: 'btn-outline-info',
			text:      '<i class="fas fa-copy"></i>',
			titleAttr: 'Copy'
		    },
		    {
			extend:    'excelHtml5',
			className: 'btn-outline-info',
			text:      '<i class="fas fa-file-excel" style="color:#7A9673;"></i>',
			titleAttr: 'Excel'
		    },
		    {
			extend:    'csvHtml5',
			className: 'btn-outline-info',
			text:      '<i class="fas fa-file-alt" style="color:#758B9E;"></i>',
			titleAttr: 'CSV'
		    },
		    {
			extend:    'pdfHtml5',
			className: 'btn-outline-info',
			text:      '<i class="fas fa-file-pdf" style="color:#F4A9A9;"></i>',
			titleAttr: 'PDF'
		    }
		]
	}).buttons().container().prependTo('#page-footer')

	$("#vm-table_wrapper").width('99%');
	adjustTableNav($("#vm-table_wrapper"));

	$('.buttons-html5').each(function() {
		$(this).removeClass('btn-secondary');
	});
	renderTips();
        var table = $('#vm-table').DataTable();
	if(!isManager()){
		table.column( 2 ).visible( false );
	}
}
function drawAgentsTable(data ) {
        setupPage(" <a href='#' class='go_agents'>All Agents</a>", "All Agents");
        var btn = `<div class='row'>
                <div class='col'>
                        <div class='btn-group'>
                        <button type='button' class='btn btn-info btn-flat dropdown-toggle add-agent' >Register Agent</button> 
			</div>
                </div>
                `;
        $("#header-controls").html(btn);
        $("#dataTableDiv").append("<div id='dataTable'></div>");

        var row = "<table class='table table-bordered table-hover table-striped' id='vm-table' style='width:100%'>";
        row += "<thead><tr><th>System Name</th><th>Managing A3</th><th>Agent Details</th>";
        row += "<th>Backups</th>";
        row += "<th>Total Size</th>";
        row += "<th width='150px;'>Forget</th>";
        row += "</tr></thead><tbody>";

        if(data.Agents.length ==0){
                var row = "<div class='row bg-info m-2'><div class='col-12 panel-inactive rounded align-middle p-4' style='text-align:center;'><h3 >No Agents could be found.</h3><br></div></div>";
                $("#dataTable").append(row);
                return;
        }

        jQuery.each(data.Agents, function(i,r){
                row += "<tr />";
                var agent = "";
                // we have stored data from the agent
                if(r.authProfile !== null){
                         try{
                                var ag =JSON.parse(r.authProfile);
                                agent = ag.version.split(" ")[0] +" [seen: "+ timeSince(ag.lastCheck )+"]";
                        } catch(err){ agent="";}
                }
                //var head = "<td>" + getVirtTypeIcon(r.type) + "</td>";
                var head="";
                var guid = r.uuid;
                if(r.guid){ guid = r.guid; }
                head += "<td class='clickable view-vm-details' data-uuid='"+guid+"' data-site="+r.a3id+">"+getPowerState(r.powerState) +" "+ r.name + "</td>";
                head += "<td class='clickable view-a3' data-id='"+r.a3id+"'> "+r.a3Name+" </td>";
                head += "<td class='clickable view-vm-details' data-uuid='"+guid+"' data-site="+r.a3id+"> "+agent+" </td>";

                var lepoch = 0;
                var ts;
                var lc = " ";

		lc = "jld_stat_active";
		var ts = "No backups yet";
		if(r.lastJobResult > 0){
			ts = r.lastJobResult +" Backups";
		}

               head += "<td >" + ts + "</td>";
                var last = "N/A";
                var lastClass= "";
                var click="";
                var clickF="";
                var spanClass="jld_stat_active";
                var spanClassF="jld_stat_active";

                var sz = prettySize(r.totalSize);
                if(r.totalSize == "0"){ sz = "N/A"; }
                head += "<td class='hideable-column'> "+ sz+"</td>";

		var trash = `<i class='clickable fas fa-trash agent-delete' fa-lg data-guid="${guid}" data-toggle='tooltip' title='Forget and remove this agent'></i>`;
		if(r.lastJobResult > 0){
			trash = `<i class='fas fa-trash disabled' fa-lg data-toggle='tooltip' title='Cannot remove agents with backups!' style='color:#8A9398;cursor:not-allowed;'></i>`;

		}
		head += "<td class='text-right text-xs-right'>" + trash + "</td>";

                head += "</tr>";
                row += head;
        });
        $("#dataTableDiv").append( row);
        $("#vm-table").DataTable({
                pageLength: 20,
                lengthMenu: [5, 10,15, 20, 50, 100, 200, 500],
                language: {
                      lengthMenu: "Display _MENU_",
                },
                buttons: [
                    {
                        extend:    'copyHtml5',
                        className: 'btn-outline-info',
                        text:      '<i class="fas fa-copy"></i>',
                        titleAttr: 'Copy'
                    },
                    {
                        extend:    'excelHtml5',

                        className: 'btn-outline-info',
                        text:      '<i class="fas fa-file-excel" style="color:#7A9673;"></i>',
                        titleAttr: 'Excel'
                    },
                    {
                        extend:    'csvHtml5',
                        className: 'btn-outline-info',
                        text:      '<i class="fas fa-file-alt" style="color:#758B9E;"></i>',
                        titleAttr: 'CSV'
                    },
                    {
                        extend:    'pdfHtml5',
                        className: 'btn-outline-info',
                        text:      '<i class="fas fa-file-pdf" style="color:#F4A9A9;"></i>',
                        titleAttr: 'PDF'
                    }
                ]
        }).buttons().container().prependTo('#page-footer')


	adjustTableNav($("#vm-table_wrapper"));

        $('.buttons-html5').each(function() {
                $(this).removeClass('btn-secondary');
        });
	renderTips();
        var table = $('#vm-table').DataTable();
	if(!isManager()){
		table.column( 1 ).visible( false );
	}
}


function drawHostsTable(data) {
        setupPage(" <a href='#' class='go_hosts'>All Hosts</a>", "All Hosts");
        var btn = `<div class='row'>
		<div class='col'>
			<div class='btn-group'>
			<button type='button' class='btn btn-info btn-flat dropdown-toggle' data-toggle='dropdown' aria-expanded='false'>Add New Host Server</button> 
			<div class='dropdown-menu' role='menu'>
				<a class='dropdown-item host-add-xen' href='#'>Xen Host</a>
				<a class='dropdown-item host-add-hv' href='#'>Hyper-V Host</a>
			</div> &nbsp; 
		</div>
		</div>
		`;
        $("#header-controls").html(btn);
        $("#dataTableDiv").append("<div id='dataTable'></div>");
	if(!checkNoA3s()){ return; }
        if(data.hosts.length==0){
                var row = "<div class='row bg-info m-2'><div class='col-12 panel-inactive rounded align-middle p-4' style='text-align:center;'><h3 >There are no Hosts defined yet</h3></div></div>";
                $("#dataTable").append(row);
                return;
        }
	var row = `<table class='table table-sm table-hover ' width=99% id='hostTable'><thead><tr><th>Name</th><th>OS</th><th>Guid</th><th>CPUs</th>`;
	if(isManager()){
		row +="<th>A3s</th>";
	}
	row += `<th>Refresh <i class='kb_metarefresh clickable fas fa-question-circle host-refresh' data-toggle='tooltip' title='Run Meta-refresh job.  This is not typically needed as refresh jobs run automatically every 30 minutes.  Click to read related KB Article'></i> </th><th width='100px;'></th></tr></thead><tbody>`;
	row += "</tbody></table>";
	$("#dataTable").append(row);

	var a3s = JSON.parse(sessionStorage.getItem("a3Cache"));

	let style1 = "background: rgba(51, 51, 255, .1)";
	let style2 = "background: rgba(51, 255, 51, .1)";

	var pool= "";
	var pclass= "";
        jQuery.each(data.hosts, function(i,host){
		if(host.type != 2){ pclass = "";}
		else if(pool != host.poolid){
			pool = host.poolid;
			pclass = (pclass === style1) ? style2 : style1;
		}

		var role = "";
                if(host.type  ==2 && host.role == 1){ role = "<img src='/images/crown_32.png' width='16' height='16' data-toggle='tooltip' title='Pool Master'>"; }
                if(host.os.includes("XCP")){ host.type = "XCP"; }
                var icon = getVirtTypeIcon(host.type);
		var osinfo = getOsVersion(host.os);

		let a3Opts="";
		$.each(a3s, function(i, a3){
			a3Opts += `<option value='${a3.guid}'>${a3.name}</option>`;
		});

		row = `<tr style='${pclass}'>
			<td class='clickable host-edit' data-guid='${host.guid}' data-ip='${host.ip}' data-user='${host.username}'><i class='fas fa-edit'></i> ${host.name} ${role}  [${host.ip}]	</td>
			<td>${icon} ${host.os} ${osinfo}	</td>
			<td>${host.guid}	</td>
			<td>${host.cpu}	</td>`;
		if(isManager()){
			row += `<td><select  data-guid='${host.guid}' id="host-a3s-${host.guid}" multiple size="1">${a3Opts} </select>	</td>`;
		}
		row += `<td> <i class='clickable fas fa-sync-alt host-refresh fa-lg' data-guid="${host.guid}" data-toggle='tooltip' title='Run Meta-refresh job for host ${host.name}.'></i>	&nbsp; </td>
			<td > <i class='clickable fas fa-trash host-delete' fa-lg data-guid="${host.guid}" data-toggle='tooltip' title='Remove this Host globally'></i>	</td>
		`;
		$("#hostTable").append(row);
		if(isManager()){
			$('.SlectBox').SumoSelect();
			var sel = $("#host-a3s-"+host.guid).SumoSelect();
			$.each(host.a3s, function(i, a){
				sel.sumo.selectItem(a.guid);
			});
			$("#host-a3s-"+host.guid).addClass('host-a3');	// add this after we add selects to avoid event triggers
		}
	});
}


function drawHostsTableV1(data) {
	setupPage(" <a href='#' class='go_hosts'>All Hosts</a>", "All Hosts");
	var btn = "<button type='button' class='btn btn-info btn-block host-add'><i class='fa fa-bell'> Add New Host</i></button> &nbsp;";
	$("#header-controls").html(btn);
        $("#dataTableDiv").append("<div id='dataTable'></div>");
	if(!checkNoA3s()){ return; }
        if(data.A3s.length==0){
                var row = "<div class='row bg-info m-2'><div class='col-12 panel-inactive rounded align-middle p-4' style='text-align:center;'><h3 >There are no Hosts defined yet</h3></div></div>";
                $("#dataTable").append(row);
		return;
        }
        jQuery.each(data.A3s, function(i,a3){
		var viz = "";
                var row = "<div class='row bg-info m-2 expando ";
		if(data.A3s.length > 1){ 
			row += "clickable collapse-arrow";
			viz = "style='display: none;'"; 
		}
		row += "'><div class='col-12 panel-complete rounded align-middle p-2'><h5 ><i class='fas fa-angle-right' fa-lg'> </i> Hosts defined on "+a3.name+"</h5></div></div>";
                $("#dataTable").append(row);
		row = "<div "+viz+"><table class='table table-bordered table-hover table-striped' id='vm-table' style='width:100%'>";
		row += "<thead><tr ><th >Host Name</th><th>Hypervisor or OS</th><th>Licensed</th>";
		row += "<th>CPU Sockets</th>";
		row += "<th>UUID</th>";
		row += "<th>Pool or Agent Info</th>";
		row += "</tr></thead><tbody>";
		// first Xen stuff
		jQuery.each(a3.pools, function(key, val){
			row += "<tr><td colspan=6><div class='row'><div class='col-12'>"+val+"</div></div></td></tr>";	
			jQuery.each(a3.hosts, function(i,r){
				if(r.poolid == key){ row += drawHostRow(r); }
			});
		});

		if (a3.hosts.some(obj => obj.type == 3)){
		// now HV
			row += "<tr><td colspan=6><div class='row'><div class='col-12'>Hyper-V Hosts</div></div></td></tr>";	
			jQuery.each(a3.hosts, function(i,r){
				if(r.type == 3){ row += drawHostRow(r); }
			});
		}
		if (a3.hosts.some(obj => obj.type == 10)){
			// now old Phys
			row += "<tr><td colspan=6><div class='row'><div class='col-12'>Physical Systems</div></div></td></tr>";	
			jQuery.each(a3.hosts, function(i,r){
				if(r.type == 10){ row += drawHostRow(r); }
			});
		}
		row += "</tbody></table></div>";
		$("#dataTable").append(row);
        });
	$("#dataTable").append(row);
}

function drawHostRow(r){
                var row = "<tr />";
                var head ="<td >";
                head += "<div class='cmenu'><ul class='seeThru'><li><a href='#'><span class='spin'><img src='/images/options-icon.png' /></span></a><ul>";
                head += "<li data-id="+r.hostID+" class='host-edit clickable'><span><img src='/images/edit_icon.png' border=0 alt='rss'> "+getText("edit-host-menu")+"</span></li>";
                head += "<li data-id="+r.hostID+" class='host-refresh clickable'><span><img src='/images/refresh_icon.png' border=0 alt='rss'> "+getText("meta-host-menu")+"</span></li>";
                head += "<li data-id="+r.hostID+" class='host-delete clickable'><span><img src='/images/delete_icon.png' border=0 alt='rss'> "+getText("del-host-menu")+"</span></li>";
                head += "</ul></li></ul></div></td>";
                var role = "";
                if(r.type  ==2 && r.role == 1){
                        role = "<img src='/images/crown_32.png' width='16' height='16' data-toggle='tooltip' title='Pool Master'>";
                }
                row += "<td data-id="+r.hostID+"> " + r.name +" ("+r.ip + ")  "+role+"</td>";
                var tp = r.type;
                if(r.os.includes("XCP")){ tp = "XCP"; }
                var icon = getVirtTypeIcon(tp);
                row += "<td  data-id="+r.hostID+" >" + icon + " "+r.os+" "+ getOsVersion(r.os)+"</td>";
                var lclass = "alert-success";
		var checked = "checked";
                if(r.isLicensed==0){
			checked = "";
                        lclass = "alert-warning";
                }
                var c = r.cpu;
                if(c==0){ c = "N/A"; }
                row += "<td >" + c + "</td>";
                row += "<td >" + r.guid + "</td>";
                var pname = "N/A";
                if(r.type ==2 ){
                        pname = r.poolName;
                } else {
                        try{
                                var agent =JSON.parse(r.username);
                                pname = "<span class='success'>Agent version: "+ agent.version.split(" ")[0]+" </span> &nbsp; [Last seen: "+ getDate(agent.lastCheck, false) +"]" ;
                        }
                        catch(err){ pname ="QSB Agent information not found";}
                }
                row += "<td >" + pname + "</td>";
                row += "</tr>";
	return row;
}


function drawDash(){
	setupPage(" <a href='#' class='go_dash'>Dashboard</a>", "System Overview");

	let tmp = `<div class='col-12 p-4'><center>
			<div><h4>Loading dashboard data, please wait</h4><br></div>
			<div class='mexican-wave' ></div>
			</center>

		</div>`;

        $("#dataTableDiv").append(tmp);
	wsCall("dashData", null, "drawDashInitial", null);
}
function drawDashInitial(data){
        if(data.result != "success"){
                doToast(data.message, "Failed to get A3 details", "error");
                return;
        }

        var stats = data.stats;

        $("#dataTableDiv").html("<div id='dataTable'></div>");

	let statusBody = "<div id='dash-status' style='height:175px;'><i>Loading Status details</i></div>";
	let graphBody = `
			<div class='row'>
			<div class='chart-container' style='height: 175px; width:100%' >
			<canvas id='perfGraph' ><div class='no-data'></div></canvas></div>
			</div>`;
	let jobsBody = `<div id='dash-active-jobs'></div>`;
	var alertBody = `<table id='dash-alerts' style='width:100%; height:100%;'>
			<tr><td colspan=2><center><div class='mexican-wave'></div></center></td></tr>
			</table>`;

        var row = `<div class='container-fluid' id='dash-inner'>

        <div class='row'>
	        <div class='col-8 '>`;
			row += printPanel("Storage History", graphBody, "panel-secondary");

	row += `</div>
	        <div class='col-4 '>`;
			row += printPanel("Status", statusBody, "panel-secondary");
	row +=`	</div>
	</div>

        <div class='row'>
		<div class='col-8 ' style='height: 275px';>
			<div class='container'>
			<div class="card panel-secondary"><div class="card-header">
                <h3 class="card-title" style="width: 100%;"> Alerts </h3>
                </div><div class="card-body overflow-auto" style='height: 210px;'> ${alertBody} </div></div>
			</div>
		</div>	
		<div class='col-4' style='height: 275px';>
			<div class='container'>
			<div class="card panel-secondary"><div class="card-header">
                <h3 class="card-title go_jobs-run clickable" style="width: 100%;"> Active Jobs </h3>
                </div><div class="card-body overflow-auto" style='height: 210px;'> ${jobsBody} </div></div>
			</div>
		</div>	`;


	row +=`	</div> </div>`;

	row += `<div class='row'>`;
        $("#dataTable").append(row);
	drawDashStatus(data);

	// full stack dash here, or a manager w/ just 1 A3 node
	if(data.mode == 2 || data.a3s.length==1){
		row = ` <div class='col-12 go_a3-details clickable' data-id=${data.a3s[0].id}>`;
			let cpu = 23;
			let mem = Math.round((data.memFree/data.memTotal)* 100);

		let nodsBox =`<div class="ribbon-wrapper"> <div class="ribbon bg-warning text-xs">&nbsp; DR Only &nbsp;</div> </div>`;
		nodsBox ='';
		if(typeof window.subInfo != "undefined" && window.subInfo.edition.length && window.subInfo.edition ==2){ nodsBox = ''; }

			// put the stats boxes here
			let s = `<div id='dash-stats' class='d-flex justify-content-between'>

				<div class='p-1'>
				<center><div ><canvas id='adsPie' width='100' height='100'></canvas></div><span class='badge badge-secondary'>ADS Usage</span></center>
				</div>
				<div class='p-1'>
		<div class="position-relative" >
				${nodsBox}

				<center><div >
<canvas id='odsPie' width='100' height='100'></canvas></div><span class='badge badge-secondary' id='odsLabel'>ODS Usage</span></center>
                      </div>
				</div>
				<div class='p-1'>
				<center><div ><canvas id='a3sysPie' width='100' height='100'></canvas></div><span class='badge badge-secondary'>A3 System Disk</span></center>
				</div>
				<div class='p-1'>
				<center><div ><canvas id='srPie' width='100' height='100'></canvas></div><span class='badge badge-secondary'>Alike SR Storage</span></center>
				</div>
					<div class='p-2' data-toggle='tooltip' title='%CPU Usage'>
					<center><div >
						${showKnob("cpu", data.cpuPerc,"")}
						</div>
					<span class='badge badge-secondary'>A3 CPU Usage</span></center>
					</div>
					<div class='p-2' data-toggle='tooltip' title='%Memory in use'>
					<center><div >
						${showKnob("mem", mem,"")}
						</div>
					<span class='badge badge-secondary'>A3 Memory Usage</span></center>
					</div>
				<div class='p-2 align-bottom'>

				 <table width='320px;'>
<tr id='net-box'>
        <td ><span style='color: #00bf00' class='text-nowrap'><i class='fas fa-arrow-up fa-lg'></i> <span id='cur-tx'>0</span></span><br>
        <span style='color: #007bff' class='text-nowrap'><i class='fas fa-arrow-down fa-lg'></i> <span id='cur-rx'>0</span> </span></td>
                <td colspan=3> 
                <span id='net-tx'></span>
                <span id='net-rx'></span>
                </td>
        </tr>


				</table>
					<center><span class='badge badge-secondary'>Network Usage</span></center>
				</div>
			</div>
				`;

			row += printPanel("System Stats", s, "panel-secondary");
			row += `</div> `;

		$("#dash-inner").append(row);

		if(data.odsStatus !=1){
			$("#odsPie").prop('disabled', true);
			$("#odsLabel").html("ODS Not Defined");
		}

		drawDiskPie(data.adsUsed, data.adsFree, "adsPie", false);
		drawDiskPie(data.odsUsed, data.odsFree, "odsPie", false);
		drawDiskPie(data.localDisk.total - data.localDisk.free, data.localDisk.free, "a3sysPie", false);
		drawDiskPie(data.dbDisk.total - data.dbDisk.free, data.dbDisk.free, "srPie", false);
		let wide = $("#net-box").width() -50;
		let tx=[0,0,0,0,0,0,0,0,0,0,0,0,0,0,0];
		$("#net-rx").sparkline(tx, { fillColor: false, changeRangeMin: 0, chartRangeMax: tx.length ,lineWidth: 2,height: 50, width: wide, lineColor: '#00bf00' });
		$("#net-rx").sparkline(tx, {composite: true, fillColor: false, changeRangeMin: 0, chartRangeMax: tx.length,lineWidth: 2,height: 50, width: wide, lineColor: '#007bff'  });
		pollSysUse(1, wide);
	} else if(data.a3s.length ==0){
		row = ` <div class='col-12 go_a3s clickable' >
			<center>
			<h4>No A3s defined</h4><br>
			<button class='btn btn-warning go_a3s clickable'>Add A3 Now</button>
			<br> &nbsp;
			</center>
			</div> `;
		$("#dash-inner").append(row);
	} else if(data.a3s.length == -22){	// lets table this one for now
		let wd = 'col-6';
		if(data.a3s.length == 3){ wd = 'col-4'; }
		row = ` <div class='row' > `;
		
		$.each(data.a3s, function(i, a){
			row += `<div class='${wd} go_a3-details clickable' data-id=${a.id}>`; 
			let s = drawA3PanelMed(a);
			row += printPanel(a.name, s, "panel-secondary");
			row += `</div>`;
		});
		row += "</div>";
		$("#dash-inner").append(row);
	}else {
		row = ` <div class='fluid-container'>`;
		let s  = `<table class='table table-sm table-bordered table-hover table-striped' id='a3dashlist'>
			<thead><tr><th>A3 Name</th><th>Address</th><th>Guid</th><th>Build</th><th>Status</th><th>Last Seen</th><th>Details</th><th>Alerts</th></tr></thead><tbody></tbody></table>`;
		row += printPanel("A3 list", s, "panel-secondary");
		row += "</div>";
		$("#dash-inner").append(row);
		$.each(data.a3s, function(i, r){
			drawA3Row(r, $("#a3dashlist tbody"), false);
		});
	}


	var args= new Object();
	args["active"] = 1;
	wsCall("jobgraph", args, "drawDashGraph", null);
	wsCall("jobs", args, "drawDashJobs", null);
	wsCall("alerts/get", null, "drawDashAlerts", null);


	$(".knob").knob({'readOnly':true });
}

function drawA3PanelMed(a){
	let s = `<div> A3 ${a.name}</div>`;
	return s;
}

function drawDashGraph(data){
        if(data.result != "success"){
                doToast(data.message, "Failed to get history graph data", "warning");
                return;
        }
        if(data.dates){
                var dz =[];
                $.each(data.dates, function(i,d){ dz[dz.length]=getDateCust(d,false); });
                drawUsageGraph(dz,data.protected,data.deltas,data.dedup,"perfGraph");
        }
}

function drawUsageGraph(versions, totals, delta, dedup, element){
        var chartData = {
          labels: versions,
          datasets: [
                {
                label: "Storage Consumed",
                fill: "origin",
                borderColor: "#f5f902",
		pointRadius: '0',
                backgroundColor: "#e4e806",
                data: dedup
            },
            {
                label: "Changed Data",
                fill: "origin",
                borderColor: "#5e99d8",
		borderJoinStyle: 'round',
		pointRadius: '0',
                backgroundColor: "#4f84bc",
                data: delta
            },
            {
                label: "Total Protected",
                fill: "origin",
                borderColor: "#01a01a",
		borderJoinStyle: 'round',
		pointRadius: '0',
                backgroundColor: "#227c07",
                data: totals
            }
          ]
        };
        var options={
                responsive : true,
                tension : 0.2,
                maintainAspectRatio: false,
                hover: {
                        mode: 'nearest',
                        intersect: true
                },
                plugins:{
			legend: { display: false },
			tooltip: {
				mode: 'index',
				intersect: false,
				callbacks: {
				    label: function(context) {
					return " "+context.dataset.label+" "+prettySize(context.parsed.y) || '';
				    }
				}
			}
                },
                scales: {
                        y: { display: false  },
			x:{ display: false  },
                        yAxes: { display: false },
                }
        };
        var ctx = document.getElementById(element);
        ctx = new Chart(ctx, {type: 'line', data: chartData, options: options});
}


function drawDashStatus(data){
        if(data.result != "success"){
                doToast(data.message, "Failed to get Status details!", "error");
                return;
        }

	let javaStat ="badge-success", blkfsStat = javaStat, instafsStat = javaStat;
	if(!data.instafsState){ instafsStat = "badge-danger"; }
	if(!data.blkfsState){ blkfsStat = "badge-danger"; }
	if(!data.javaState){ javaStat = "badge-danger"; }
	let guy = $("#dash-status");
	guy.empty();
	let p= `<table><tr>`;
	if(data.mode == 2){
		p += `<td> Services </td>
		<td> <span class='badge ${javaStat}' data-toggle='tooltip' title='Responsible for Backup & Restore data processing'> Engine </span> </td>
		<td> <span class='badge ${blkfsStat}' data-toggle='tooltip' title='Creates virtual files, folders, and disks for restores'>  RestoreFS </span> </td>
		<td> <span class='badge ${instafsStat}' data-toggle='tooltip' title='Used for ABDs and InstaBoot restores'> AlikeSR </span> </td>
		</tr>`;
	}
	p +=`	<tr>
			<td> A3 Build: </td>
			<td > ${data.build} </td>
		`;
	let mode = "Full Node";
	if(data.mode ==0){ mode = "Manager Only"; }
	p+= `<td>A3 Mode: </td> <td > ${mode}</td></tr>`;

	let which = "A3 GUID";
	if(data.mode==0){ which = "Manager Guid:"; }
	p += `<tr> <td> ${which}: </td> <td colspan=3> ${data.guid} </td> </tr>`;
	p += "</table>";
	guy.append(p);
}

function drawDashAlerts(data){
        if(data.result != "success"){
                doToast(data.message, "Failed to get Alerts!", "error");
                return;
        }
	$("#dash-alerts tr").remove();
	if(data.alerts.length ==0){
		let tmp = `<tr><td colspan=2 align='middle'>No alerts to display</td></tr>`;
		$("#dash-alerts").append(tmp);
		return;
	}
	$("#dash-alerts").css("height", 'auto');

	$("#dash-alerts").addClass("table-striped");
	$("#dash-alerts").addClass("table-hover");
	$.each(data.alerts, function(i, a){
                let tmp = `<tr>
                        <td class='shrinkFit' data-toggle='tooltip' title='${getDate(a.timestamp)}'>${timeSince(a.timestamp)} </td>`;
                if(window.nodeMode == 0){
                        tmp += `<td class='shrinkFit'> [<span data-id=${a.source} class='clickable go_a3-details' style='color:#0000ff'>${a.sourceName}</span>]</td>`;
                }       
                        tmp += `<td> &nbsp; ${a.message}</td>
                        <td width='50px'><i class='fas fa-trash clickable delete-alert' data-toggle='tooltip' title='Delete Alert' data-id='${a.id}' ></i></td>
                        </tr>`;

		$("#dash-alerts").append(tmp);

	});

}
function drawDashJobs(data){
        if(data.result != "success"){
                doToast(data.message, "Failed to get active jobs!", "error");
                return;
        }
        $("#dash-active-jobs tr").remove();
        if(data.jobs.length ==0){
                let tmp = `<tr><td colspan=2>No jobs are currently running</td></tr>`;
                $("#dash-active-jobs").append(tmp);
        }
        $.each(data.jobs, function(i, j){
		let jerb = `<div data-id='${j.jobID}' data-site='${j.a3id}' class='go_joblog clickable row'>
				<div class='col-12 text-nowrap text-truncate'>${j.name} [${j.jobID}] </div>
				<div class='col'> ${makePB(Math.round(j.progress), j.status)} </div> 
			</div>`;
                $("#dash-active-jobs").append(jerb);

        });

}


//////////////////////////////////////// Quick Restore stuff here ////////////////////////////////////////////////
//

function showQuickRestore(name, uuid, ts, site, ds){
	prepModal("Quick Restore");

        $("#modal-ok-button").html('Begin Restore');
        $("#modal-ok-button").addClass('quick-restore-start');

        $("#modal-ok-button").prop('disabled', true);

        var title = "Restoring: <span class='text-olive'>"+name+"</span>";
	var vhdTip = printTip('If left blank, the default VHD path configured on your Hyper-V server will be used.<br><br>If specified, use <b>a local path relative to the Hyper-V host</b> (eg. D:\\Hyper-V\\VHDs)');
	//let tempTip = printTip("Select the most suitable template for your VM.  UEFI based VMs <b>must</b> select the proper UEFI based template to boot properly.  Most MBR based systems can safely choose 'Other install media', but please read our related KB article if you are unsure what to use.");
	let netTip = printTip("Select the network for your VM.  This network list is based on the host/pool selected above.");
        var h = `<br><center><h4>${title} </h4></center> 
		<span id='vm-data' data-uuid='${uuid}' data-site=${site} data-ts=${ts} data-ds=${ds}></span>
			<table width=100%><tr><td class='text-nowrap' width=50>Choose Destination:</td><td>
			<select class='form-control rounded-0' id='dest-uuid' >
				<option value="#"> Loading options, please wait... </option>
			</select>
			</td></tr>
			<tr id='sr-row' style='display: none;'><td>Choose Storage</td><td>
			<select class='form-control rounded-0' id='dest-sr' ></select>
			</td></tr>
			<tr id='vhd-row' style='display: none;'><td>VHD Path ${vhdTip}</td><td>
			<input class='form-control rounded-0' id='dest-vhd' placeholder='<Override Default VHD Path>'></select>
			</td></tr>
			<!-- <tr id='template-row' style='display: none;'><td style="line-height:5px;">Select Template </td><td>
			<select class='form-control rounded-0' id='dest-template' ></select>
			</td></tr>-->
			<tr id='network-row' style='display: none;'><td style="line-height:5px;">Select Network ${netTip}</td><td>
			<select class='form-control rounded-0' id='dest-network' ></select>
			</td></tr>
			<tr id='disk-row' style='display: none;'><td valign='top'>Select Disks</td><td>
			<div class='form-control2 rounded-0' id='dest-disks' ></div>
			</td></tr>
		</table>
		<div class='row alert ' id='quick-restore-msg'> </div>
		`;
        $("#modalBody").html(h);

	let cache = sessionStorage.getItem("poolCache");
	if(cache === null || cache.trim() == ''){
		buildMetaCaches();
		doToast("Meta data was not found.  Rebuilding now, please try again in a moment.","Meta data rebuilding.  Retry again", "warning");	
		return;
	}else{
		cache = JSON.parse(cache);
		populateQuickRestoreItems(cache, site);
	}
	var args= new Object();
	args["uuid"] = uuid;
	args["ts"] = ts;
	wsCall("version/info", args, "populateQuickRestoreDisks", null, site);
        displayModal(true);
	renderTips();
}

function populateQuickRestoreDisks(data){
	$("#dest-disks").empty();
	$("#disk-row").show();
	$.each(data.info.Disks, function(i, d){
		let guy = `<input type='checkbox' class='quick-rest-disk' data-diskid=${d.filename} style='margin-right:5px;' checked> <i class="far fa-hdd fa-sm"></i> &nbsp;
			<span title='Disk ${d.filename}'>Disk ${(d.filename)} [${prettySize(d.size)}] </span><br>`;
		$("#dest-disks").append(guy);
	});

	$(".quick-rest-disk").on("change", function() {
		let noDisks = $('.quick-rest-disk[type="checkbox"]:checked').length === 0;
		if(noDisks){ 
			$("#modal-ok-button").prop('disabled', true);
			return; 
		}
		$("#modal-ok-button").prop('disabled', false);
	});
}

function populateQuickRestoreItems(cache, site){

	$('#dest-uuid').empty();
	$('#dest-uuid').append($('<option>', { value: 0, text: 'Select your restore host or pool' }));

	$.each(cache[site].pools, function(i,p){
		if(p.type != 2 && p.type != 0 && p.type !=3){ return; }

		let nm = p.name;
		if(p.type == 0){ 			// if type ==0, loop over host and add them next
			if(Object.keys(p.hosts).length >1){
				nm = "Pool: "+p.name +" [unhomed]";
//				$('#dest-uuid').append($('<optgroup>',{ label: 'Xen Pool: '+ p.name} ));
				$('#dest-uuid').append($('<option>', { value: p.uuid, text: nm }));
			}

		}else if(p.type == 3){			// HV
			nm = "   "+p.name;
			$('#dest-uuid').append($('<option>', { value: p.uuid, text: nm }));
		}

		// standalone xen hosts are in a pool, as the only host.  
		if(p.type ==0){
			if(Object.keys(p.hosts).length ==1){
				var guy = Object.keys(p.hosts)[0];
				$('#dest-uuid').append($('<option>', {
					value: p.hosts[guy].uuid, 
					text:  p.hosts[guy].name 
				}));
			}else{
				$.each(p.hosts, function(i,h){
					$('#dest-uuid').append($('<option>', {
						value: h.uuid, 
						text: ' > '+h.name 
					}));
				});
			}
		}
	});
	$("#dest-uuid").on("change", function() {
		let uuid = $("#dest-uuid").val();
		if(uuid == 0){ 
			$("#modal-ok-button").prop('disabled', true);
			return; 
		}
		showSrsForUuid(uuid );
		showNetworksForUuid(uuid);
		if( $("#cbt").prop('checked') == true) {	;
		}else{
//			showTemplatesForUuid(uuid, site );
		}
		$("#modal-ok-button").prop('disabled', false);
	});
}

function showSrsForUuid(uuid){
	let h = getHostForGuid(uuid);
	if (h.type ==3){
		$("#sr-row").hide();
		$("#vhd-row").show();
		//$("#template-row").hide();
		$("#network-row").hide();
		return;
	}
	$("#vhd-row").hide();
	$("#sr-row").show();

	let srs = JSON.parse(sessionStorage.getItem("srCache"));
	$("#sr-row").show();
	$("#dest-sr").html('');

	$.each(srs[uuid], function(i,s){
		let nm = s.name + '  ('+ prettySize(s.free) + ' Free) ';
		$("#dest-sr").append($('<option>',{
			value: s.uuid,
			text: nm
		}));
	});
	$("#dest-sr").append($('<option>',{ value: "AlikeSR", text: "AlikeSR - (Boot from Backup)" }));
	$("#network-row").show();

//        $.each(nets[uuid], function(i,n){
//                let nm = n.name + '  ';
//                $("#dest-sr").append($('<option>',{
//                        value: n.uuid,
//                        text: nm
//                }));
//        });
//

}

function showNetworksForUuid(uuid, site){
        let nets = JSON.parse(sessionStorage.getItem("networkCache"));
	let pool = getPoolForGuid(uuid);
	let temps = nets[pool];
        $("#dest-network").html('');
        $.each(temps, function(i,s){
                $("#dest-network").append($('<option>',{
                        value: s.uuid,
                        text: s.name
                }));
        });
	$("#dest-network").append($('<option>',{
		value: "sandbox",
		text: "SandBox Net (Isolated traffic)"
	}));
}

function showTemplatesForUuid(uuid, site){
	let temps = JSON.parse(sessionStorage.getItem("templateCache"));
	let pc = JSON.parse(sessionStorage.getItem("poolCache"));
	let pool = getPoolForGuid(uuid);
	$("#dest-template").html('');
	$.each(temps[pool], function(i,s){
		$("#dest-template").append($('<option>',{
			value: s,
			text: s
		}));
	});
	$("#dest-template option[value='Other install media']").attr('selected', 'selected');
}

function getPoolForGuid(uuid){
//	let temps = JSON.parse(sessionStorage.getItem("templateCache"));
	let pc = JSON.parse(sessionStorage.getItem("poolCache"));
	let pool = null;
	$.each(pc, function(i, site){
		if(uuid in site.pools){ pool= uuid; return false; }
		else{
			$.each(site.pools, function(i, p){

				$.each(p.hosts, function(i, h){
					if(h.uuid == uuid){ 
						pool= p.uuid;
						return false;
					}
				});
			});
		}
	});
	return pool;
}
function getHostForGuid(uuid){
//        let temps = JSON.parse(sessionStorage.getItem("templateCache"));
        let pc = JSON.parse(sessionStorage.getItem("poolCache"));
        let host = null;
        $.each(pc, function(i, site){
                if(uuid in site.pools){ host= site.pools[uuid]; return false; }
                else{
                        $.each(site.pools, function(i, p){
                                $.each(p.hosts, function(i, h){
                                        if(h.uuid == uuid){
                                                host= h;
                                                return false;
                                        }
                                });
                        });
                }
        });
        return host;
}

function quickRestoreCallback(data){
        if(data.result == "success"){
                var p = data.message +"  Check active jobs for progress.";
                $("#quick-restore-msg").addClass("bg-success");
                $("#quick-restore-msg").html(p);
		$("#modal-ok-button").prop('disabled', true);
        }else{
                var p = "Failed to start job:<br>"+data.message;
                $("#quick-restore-msg").addClass("bg-danger");
                $("#quick-restore-msg").html(p);
        }
}

//////////////////////////////////////// End Quick Restore stuff ////////////////////////////////////////////////

//////////////////////////////////////// Add Agent ////////////////////////////////////////////////
function drawAddAgentModel(){
        prepModal("Register a new Agent");
        var newData = `<center><h3> Register a new Agent </h3></center>  <form id='addAgent' >
			<input type='hidden' id='a3-guid' name='a3-guid' value='0'>`;
        var a3s = JSON.parse(sessionStorage.getItem("a3Cache"));
        let a3Cnt = a3s.length;

        if(a3Cnt > 1){
                newData += "<table width='100%'><tr><td width='65%'></td><td></td></tr><tr><td>";
        }else{
                newData += "<table width='100%'><tr><td width='100%' colspan=2></td></tr><tr><td>";
        }

        newData += `<table width='95%'><tr><td>
                        <tr>
                        <td><br><input class='has-less-padding has-border full-width' type='text' placeholder='Agent IP address' name='agent-ip' id='agent-ip' ></td>
			</tr> `;

        newData += `<tr><td><a class='btn btn-primary btn-sm' id='testAgent'>Test Agent</a></td></tr>
        </table>
        </td>
        `;
        // only give them this option on adds
        if(a3Cnt > 1 ){
                let a3Opts="";
                $.each(a3s, function(i, a3){
                        a3Opts += `<option value='${a3.id}'>${a3.name}</option>`;
                });
                newData += `<td> Assign to A3:<br><select id='a3id' class='SlectBox' size=1> ${a3Opts} </select> </td>`;

        }else{
                let gd = a3s[0].id;
                newData += `<input type='hidden' id='a3id' value='${gd}' /> `;
        }
        newData += ` </tr></table> `;


        var hostNote = `
			The Full QS Agent must already be installed on the target system.<br>
			`;

        newData += `<table width='99%'><tr><td colspan=2><br><b>Note:</b><br><div id='hostNote'>${hostNote}</div><br><p /></td></tr>
                        <tr><td colspan=2><input class='btn btn-block btn-success disabled' type='submit' value='Save' id='add-agent-save'></td></tr>
                        </form></table>
                <div id='agent-modal-error' class='modal-error'></div>`;

        $("#modalBody").html(newData);
	$('.SlectBox').SumoSelect();
	var sel = $("#a3id").SumoSelect();

        displayModal(true);
}
async function doTestAgent(){
        $("#agent-modal-error").removeClass();
        $("#agent-modal-error").html("Testing access now...");
        var args= new Object();
        var ip =$("#agent-ip").val();
        var ipPattern = /^(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$/;
        if (!ipPattern.test(ip)) { ip=""; }
        if(!ip || ip=="" ){
            $("#agent-modal-error").addClass("modal-error");
            $("#agent-modal-error").html("Please provide a valid IP address.");
            return;
        }
        args["ip"] = ip;
        args["virtType"] = 10;

        let a3 = $("#a3id").val();
        a3s = Array.isArray(a3) ? a3 : [a3];

        try{
                var res = await wsPromise("testAgent", args, a3s[0])
//                sessionStorage.setItem("tmpHost", JSON.stringify(res.host));
                $("#agent-modal-error").removeClass();
                $("#agent-modal-error").addClass("modal-success");
                $("#agent-modal-error").html("Authentication succeeded");
                $("#host-passed").val(1);
                $("#add-agent-save").removeClass("disabled");
        }catch (ex){
                $("#agent-modal-error").removeClass();
                $("#agent-modal-error").addClass("modal-error");
                $("#agent-modal-error").html(ex.message);
        }
}

function addAgentCallback(data){
        $("#agent-modal-error").removeClass();
        $("#agent-modal-error").html(data.message);
	if(data.result == "success"){
		displayModal(false);
                wsCall("agents", null, "drawAgentsTable", null);
	}else{
		doToast(data.message, "Failed to register Agent", "warning");
	}

}
//////////////////////////////////////// End Add Agent ////////////////////////////////////////////////

//////////////////////////////////////// Add/Edit Host stuff here ////////////////////////////////////////////////
function drawEditHostModal(data, type=2 ){
	var isHv = false;
	if(type ==3){ isHv = true; }

	let isEdit = false;
	
        var title = "Add a Xen host/pool";
	if (isHv){ title = "Add a Hyper-V Host"; }
	
	if(data != null){ 
		title = "Edit Host"; 
		isEdit=true;
	}

	prepModal(title);
        var newData = "<center><h3>"+title+" </h3></center>  <form id='editHost' >";
        var pass = "";
        var ip = "";
        var user = "";
        if(isEdit){
                user = data.user;
		if(pass){ pass = data.pass; }
                ip = data.ip;
                newData += "<input type='hidden' id='isNew' name='isNew' value='0'>";
                newData += `<input type='hidden' id='hostguid' name='hostguid' value='${data.guid}'>`;       // we need them to re-test
        }else{
                newData += "<input type='hidden' id='isNew' name='isNew' value='1'>";
                newData += "<input type='hidden' id='a3-guid' name='a3-guid' value='0'>";
                newData += "<input type='hidden' id='a3-name' name='a3-name' value='New A3'>";
        }
	if(isHv){ newData += "<input type='hidden' id='virtType' value=3>"; }
	else{ newData += "<input type='hidden' id='virtType' value=2>"; }

	newData += "<input type='hidden' id='host-passed' value=0>"; 

	var a3s = JSON.parse(sessionStorage.getItem("a3Cache"));
	let a3Cnt = a3s.length;

	if(a3Cnt > 1){
		newData += "<table width='100%'><tr><td width='65%'></td><td></td></tr><tr><td>";
	}else{
		newData += "<table width='100%'><tr><td width='100%' colspan=2></td></tr><tr><td>";
	}

        newData += `<table width='95%'><tr><td>`;
	if(isHv){
		newData += `
			<tr>
			<td><br><span id='host-address-lbl'>Address</span>: </td><td><br><input class='has-less-padding has-border full-width' type='text' placeholder='Your Host address' name='hostip' id='hostip' value='${ip}'></td></tr> `;
	}else{
		newData += `
		<tr>
		<td><span id='host-address-lbl'>Address</span>: </td><td><input class='has-less-padding has-border full-width' type='text' placeholder='Your Host address' name='hostip' id='hostip' value='${ip}'></td></tr>
		<tr>
			<td><span id='host-user-lbl'>Username</span>: </td><td><input class='has-less-padding has-border full-width' type='text' placeholder='Host username' name='hostuser' id='hostuser' value='${user}'></td>
		</tr> <tr>
			<td>Password:</td><td><input class='has-less-padding has-border full-width' placeholder='Host Password' type='password' name='hostpass' id='hostpass' value='${pass}'></td>
		</tr>`;
	}

        newData += `<tr><td></td><td><a class='btn btn-primary btn-sm' id='testHost'>Test</a></td></tr>
	</table>
	</td>
	`;
	// only give them this option on adds
	if(a3Cnt > 1 && !isEdit){
		let a3Opts="";
		$.each(a3s, function(i, a3){
			a3Opts += `<option value='${a3.id}'>${a3.name}</option>`;
		});
		newData += `<td> Add to A3:<br><select id='a3id' class='SlectBox' multiple size=1> ${a3Opts} </select> </td>`;

	}else{
		let gd = a3s[0].id;
		newData += `<input type='hidden' id='a3id' value='${gd}' /> `;
	}
	newData += ` </tr></table> `;

        var hostNote = "Enter the management IP of your host or Pool Master.";
	if(isHv){
		hostNote = "Please be sure the target host already has the <a href='https://docs.alikebackup.com/' target=_new>QS Agent</a> installed properly before testing.";
	}

        newData += `<table width='99%'><tr><td colspan=2><br><b>Note:</b><br><div id='hostNote'>${hostNote}</div><br><p /></td></tr>
			<tr><td colspan=2><input class='btn btn-block btn-success disabled' type='submit' value='Save' id='add-host-save'></td></tr>
			</form></table>
		<div id='host-modal-error' class='modal-error'></div>`;

        $("#modalBody").html(newData);
	$('.SlectBox').SumoSelect();
	var sel = $("#a3id").SumoSelect();
	if(!isEdit){
		if(data && data.a3s.length){
			$.each(data.a3s, function(i, a){
				sel.sumo.selectItem(a);
			});
		}
	}

        displayModal(true);
}

async function doTestHost(){
	let isXen = $("#virtType").val() == 2 ? true : false;

	$("#host-modal-error").removeClass();
	$("#host-modal-error").html("Testing access now...");
	var args= new Object();
        var ip =$("#hostip").val();
        var ipPattern = /^(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$/;
        if (!ipPattern.test(ip)) { ip=""; }
        if(!ip || ip=="" ){
            $("#host-modal-error").addClass("modal-error");
            $("#host-modal-error").html("Please provide a valid IP address.");
            return;
        }
        args["ip"] = ip;
        args["type"] = $("#virtType").val();
	let a3 = $("#a3id").val();
	a3s = Array.isArray(a3) ? a3 : [a3];

	if(a3s.length ==0){ 
	    $("#host-modal-error").addClass("modal-error");
	    $("#host-modal-error").html("Please select at least 1 A3");
	    return;
	}

	if(isXen){
		var pass =$("#hostpass").val();
		if(!pass || pass=="" ){
		    $("#host-modal-error").addClass("modal-error");
		    $("#host-modal-error").html("Please provide a valid password");
		    return;
		}
		args["pass"] = pass;
		var user =$("#hostuser").val();
		if(!user || user=="" ){
		    $("#host-modal-error").addClass("modal-error");
		    $("#host-modal-error").html("Please provide a valid username");
		    return;
		}
		args["pass"] = pass;
		args["user"] = user;
	}

        try{
                var res = await wsPromise("host/test", args, a3s[0])
		sessionStorage.setItem("tmpHosts", JSON.stringify(res.hosts));

		// TODO: now we have a host object in the res.host

                $("#host-modal-error").removeClass();
                $("#host-modal-error").addClass("modal-success");
                $("#host-modal-error").html("Authentication succeeded");

                $("#host-passed").val(1);

                $("#add-host-save").removeClass("disabled");
        }catch (ex){
                $("#host-modal-error").removeClass();
                $("#host-modal-error").addClass("modal-error");
                $("#host-modal-error").html(ex.message);
        }
}


async function doAddHost(data){
        $("#modal-error").removeClass();
        $('#modal-error').text("Saving...");

	let isXen = $("#virtType").val() == 2 ? true : false;

        if($("#host-passed").val()!=1){
                $("#host-modal-error").addClass("modal-error");
                $('#host-modal-error').text("Please test the connection before saving.");
                return;
        }
        if($("#hostip").val()==""){
                $("#host-modal-error").addClass("modal-error");
                $('#host-modal-error').text("Error: Invalid address");
                return;
        }
	if(isXen){
		if($("#hostpass").val()=="" ){
			$("#host-modal-error").addClass("modal-error");
			$('#host-modal-error').text("Error: Password cannot be blank");
			return;
		}
                if( $("#host-passed").val() != 1){
			$("#host-modal-error").addClass("bg-error");
			$('#host-modal-error').text("Error: Please test the connection first");
			return;
		}
	}
        if($(this).hasClass("disabled")){ return; }
        var args= new Object();
        args["ip"] = $("#hostip").val();
	if(isXen){
		args["pass"] = $("#hostpass").val();
		args["user"] = $("#hostuser").val();
	}
	args["a3s"] = $("#a3id").val();
//	args["a3s"] = Array.isArray(a3) ? a3 : [a3];


        var isNew=true;
        var action = "host/edit";
	args["host"] = JSON.parse(sessionStorage.getItem("tmpHosts"));
        if($("#isNew").val()== "1"){ 
		action = "host/add"; 
	} else{ args["guid"] = $("#hostguid").val(); }
        try{
                showLineLoad();
                var res = await wsPromise(action, args, 0)

		sessionStorage.setItem("tmpHosts", "");

		if($("#isNew").val()== "1"){ wsCall("hostPools",null,"setPoolCacheCB",0 ); }    // refresh their cache?
		doToast("Enumeration of host has started.  Please wait for the enumeration job to complete so that all VMs on this host can be detected.", "Host Added, enumeration in progress", "success");

                displayModal(false);
		wsCall("hosts", null, "drawHostsTable", null);
                finishLineLoad();
        }catch (ex){
                $("#host-modal-error").addClass("modal-error");
                $('#host-modal-error').text("Failed to add/edit Host: "+ex.message);
                $("#host-modal-error").removeClass();
                $("#host-modal-error").addClass("modal-error");
                $('#host-modal-error').text(ex.message);
                finishLineLoad();
        }
}


//////////////////////////////////////// Add/Edit A3 stuff here ////////////////////////////////////////////////
function drawEditA3Modal(data ){
        var title = "Add an A3 to Manage";
	if(data != null){ title = "Edit A3"; }
	prepModal(title);
        var newData = "<center><h3>"+title+" </h3></center>  <form id='edita3' >";
        var pass = "";
        var ip = "";
        if(data != null){
                pass = data.password;
                ip = data.ip;
                newData += "<input type='hidden' id='isNew' name='isNew' value='0'>";
                newData += "<input type='hidden' id='a3id' name='a3id' value='"+data.a3id+"'>";
                newData += "<input type='hidden' id='a3-guid' name='a3-guid' value='0'>";       // we need them to re-test
        }else{
                newData += "<input type='hidden' id='isNew' name='isNew' value='1'>";
                newData += "<input type='hidden' id='a3-guid' name='a3-guid' value='0'>";
                newData += "<input type='hidden' id='a3-name' name='a3-name' value='New A3'>";
        }

        newData += "<table width='95%'><tr><td>";
        newData += "<tr><td><span id='host-address-lbl'>A3 Address</span>: </td><td><input class='has-less-padding has-border full-width' type='text' placeholder='Your A3 address' name='a3ip' id='a3ip' value='"+ip+"'></td></tr>";
        newData += "<tr><td>A3 Manager Token:</td><td><input class='has-less-padding has-border full-width' placeholder='Remote A3 Manager Token' type='password' name='a3pass' id='a3pass' value='"+pass+"'></td></tr>";

        newData += "<tr><td>Test Connection</td><td><a class='btn btn-primary btn-sm' id='testA3'>Test</a></td></tr>";

        var a3Note = "Enter the password provided on your A3 console, or that you set on the remote A3 to link successfully.";
        newData += "<tr><td colspan=2><br><b>Note:</b><br><div id='a3Note'>"+a3Note+"</div><br><p /></td></tr>";

        newData += "<tr><td colspan=2><input class='btn btn-block btn-success disabled' type='submit' value='Save' id='add-a3-save'></td></tr>";
        newData += "</form></table>";
        newData += "<div id='a3-modal-error' class='modal-error'></div>";

        $("#modalBody").html(newData);
        displayModal(true);
}

async function doTestA3(){
    $("#a3-modal-error").removeClass();
    $("#a3-modal-error").html("Testing access now...");
    var args= new Object();
        var ip =$("#a3ip").val();
        var pass =$("#a3pass").val();
        var ipPattern = /^(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$/;
        if (!ipPattern.test(ip)) { ip=""; }
        if(!ip || ip=="" ){
            $("#a3-modal-error").addClass("modal-error");
            $("#a3-modal-error").html("Please provide a valid A3 IP.");
            return;
        }
        if(!pass || pass=="" ){
            $("#a3-modal-error").addClass("modal-error");
            $("#a3-modal-error").html("Please provide a valid password");
            return;
        }
        args["ip"] = ip;
        args["pass"] = pass;

        args["name"] = $("#a3-name").val();

        try{
                var res = await wsPromise("a3/test", args, 0)
		$("#a3-modal-error").removeClass();
		$("#a3-modal-error").addClass("modal-success");
		$("#a3-modal-error").html("Authentication succeeded");
		$("#a3-guid").val(res.guid);
		$("#a3-name").val(res.name);
		$("#add-a3-save").removeClass("disabled");
        }catch (ex){
		$("#a3-modal-error").removeClass();
		$("#a3-modal-error").addClass("modal-error");
		$("#a3-modal-error").html(ex.message);
        }
}

async function doAddA3(data){
        $("#modal-error").removeClass();
        $('#modal-error').text("Saving...");
        if($("#a3ip").val()==""){
                $("#a3-modal-error").addClass("modal-error");
                $('#a3-modal-error').text("Error: Invalid address");
                return;
        }
        if($("#a3pass").val()=="" ){
                $("#a3-modal-error").addClass("modal-error");
                $('#a3-modal-error').text("Error: Password cannot be blank");
                return;
        }
        if($("#a3-guid").val()== "0"){
                $("#a3-modal-error").addClass("bg-error");
                $('#a3-modal-error').text("Error: please test the connection first");
                return;
        }
        if($(this).hasClass("disabled")){ return; }
        var args= new Object();
        args["ip"] = $("#a3ip").val();
        args["pass"] = $("#a3pass").val();
        args["guid"] = $("#a3-guid").val();
        args["name"] = $("#a3-name").val();
        var isNew=true;
        var action = "a3/edit";
        if($("#isNew").val()== "1"){ action = "a3/add"; }
	else{ args["id"] = $("#a3id").val(); }
        try{
		showLineLoad();
                var res = await wsPromise(action, args, 0)
		displayModal(false);
		buildMetaCaches();
		listA3s();
		finishLineLoad();
        }catch (ex){
                $("#a3-modal-error").addClass("modal-error");
                $('#a3-modal-error').text("Failed to add/edit A3: "+ex.message);
		$("#a3-modal-error").removeClass();
		$("#a3-modal-error").addClass("modal-error");
		$('#a3-modal-error').text(ex.message);
		finishLineLoad();
        }
}
//////////////////////////////////////// Add/Edit A3 stuff here ////////////////////////////////////////////////


function drawJoblog(data, site){
	if(data.result != "success"){
		doToast(data.message, "Failed to get job details", "error");
		finishLineLoad();
		return;	
	}
        if(window.jldTimer){ clearTimeout(window.jldTimer); }

	if(!window.viewingJLD){ console.log("Avoiding JLD screen stealing..."); return; }	// they clicked away

	jobid = data.Job.jobID;

	if(data.Job.status >= 4){
		sessionStorage.setItem("jobid_"+site+"_"+jobid, JSON.stringify(data) )
	}

//        var data = JSON.parse(sessionStorage.getItem("jobid_"+site+"_"+jobid) );

	if(data.hasOwnProperty('timestamp')){
		job_cache.jobID = jobid;
		job_cache.timestamp = data.timestamp;
	}

        if($("#jobid").length){
                if($("#jobid").attr("data-id") == jobid){ return syncJoblog(jobid, site, data); }	// we're an update call
        }

	setupPage(" <a href='#' class='go_joblog' data-id="+jobid+" data-site="+site+">Job "+jobid+"</a>", "Job Details");
	$("#dataTableDiv").append("<div id='dataTable'></div>");

	window.viewingJLD=true;

	var showTrace = getCookie('hide-trace') === 'true' ? false : true;	
	
	var checked = "checked";
	if(showTrace){ checked = ""; }

	var chk = "<div class='form-group'> <div class='custom-control custom-switch'> <input type='checkbox' "+checked+" class='custom-control-input' id='toggle-trace'> <label class='custom-control-label' for='toggle-trace'>Hide Trace</label> </div> </div> ";
	$("#header-controls").html(chk);

	var head = buildJoblogHeader(data, site);
        $("#dataTable").append(head);
	var row = "<div id='jobid' data-id="+jobid+" ></div>";	// used to check freshness


        row  += "<table id='joblog-systems' class='table table-sm table-bordered table-hover table-striped' style='width:100%'>";
        row += "<thead><tr><th>Time</th><th>Status</th><th>Message</th></tr></thead><tbody>";

	jQuery.each(data.entries.systemEntries, function(i,sys){
		if(sys.status == 4){
			if(showTrace == true){ row += "<tr class='trace'"; }
			else{ row += "<tr class='trace' style='display: none;' "; }
		}else{ row += "<tr "; }
		row += " data-ts="+sys.timestamp+" id="+sys.id+" >";
                row +="<td nowrap width='10%'>" + getDate(sys.timestamp) + "</td>";
                row +="<td width='10px;' ><span class='"+ getStateClass(sys.status)+"'>" + getJobLogEntryStatusDesc(sys.status) + "</span></td>";
//                var msg = checkForKnownMessage(sys) + sys.description;
		msg = sys.description;
                row += "<td>" + msg + "</td></tr>";
        });
        row += "</tbody></table>";

	// no system or vaults (or reverse vaults)
	if(data.Job.type != 10 && data.Job.type != 200 && data.Job.type != 201){
//	if(data.Job.type != 10 ){
	row += "<div ><h4>&nbsp; Systems in Job</h4></div>";
	row += "<table class='table table-sm table-hover table-striped' style='width:99%'>";
	row += "<thead><tr><th class='shrinkWrap'></th><th></th><th></th></tr></thead><tbody>";

        jQuery.each(data.Job.vmsInJob, function(i,vmd){

		var pb = makeVmPB(data.vmProgress[vmd.VMID], data.Job.status);
		var st = makeVmPbStatus(data.vmProgress[vmd.VMID], data.Job.status);
                if(st.indexOf("Not Processed") !== -1 || st.indexOf("Not Started") !== -1){ 
                        var guy = data.entries.vmEntries.find(obj => obj.id === vmd.VMID);
			if(guy != null){
				if(guy.entries.length > 1){ st = "[Pending]"; } // the vm has started, but no progress has been recorded yet
				if(guy.entries.some(obj => obj.status === '1')){
					st = "[Errored]";       // we found an errored entry
				}
			}
                }

		var stanza = "<td class='shrinkFit align-middle'><i class='fas fa-angle-right fa-lg collapse-arrow'> </i> "+vmd.VMName+"</td>";
		stanza += "<td class='align-middle'> <span id='vmd_prog_"+vmd.VMID+"'>"+ pb +"</span></td>";
		stanza += "<td class='align-middle'> <span id='vmd_prog_st_"+vmd.VMID+"'>"+ st +"</span> </td>";

		var vmHead = "vm_header_"+vmd.VMID;
		row += "<tr id='"+vmHead+"' class='bg-lightblue expando clickable collapse-arrow' style='width:99%;'>";
		row += stanza;
		row += "</tr>";
		//row += "<tr class='expandable-body d-none' style='margin: 0 0 0 0;'><td colspan=3><div class='row' style='padding:0;''>";
		row += "<tr style='margin: 0 0 0 0; display: none;'><td colspan=3><div class='row' style='padding:0;''>";

		var hasData = false;
		row += "<table class='table table-sm table-bordered table-hover table-striped' style='width:100%; margin:0;'' id='vmd_"+vmd.VMID+"'>";
		row += "<thead><tr><th></th><th></th><th></th></tr></thead><tbody>";

		jQuery.each(data.entries.vmEntries, function(i,vme){
			if(vme.id == vmd.VMID){
				jQuery.each(vme.entries, function(i,e){
					var cls = "";
					var style = "";
					if(e.status == 3){ cls = "class='active_task'"; }
					else if(e.status == 4){
						cls ="class='trace'"; 
						if(showTrace == false){ style  = "style='display: none;'"; }
					}
					row += "<tr id="+e.id+" "+cls+" "+style+" data-ts="+e.timestamp+">";
					row +="<td nowrap class='shrinkFit'>" + getDate(e.timestamp) + "</td>";
					row +="<td class='shrinkFit' ><span class='"+getStateClass(e.status)+"'>" + getJobLogEntryStatusDesc(e.status) + "</span></td>";
//					var msg = checkForKnownMessage(e) + e.description;
					let msg = e.description;
					row += "<td>" + msg + "</td></tr>";
				});
				hasData =true;
				return;
			}
		});
		row += "</tbody></table>";
		row += "</div></td></tr> ";

	});
	}	// end of if type=10
	row += "</tbody></table>";
        $("#dataTable").append(row);

	if(data.Job.vmsInJob.length ==1){
		$(".expando").nextUntil('tr.expando').slideToggle(10);
	}

	finishLineLoad();

	// if the job is active, start polling for updates
        if(data.Job.status ==2 || data.Job.status ==3 || data.Job.status==0){ 
		doJLDRefresh(jobid, site); 
	}
}

// called on active jobs to update new/changed items
function syncJoblog(jobid, site, data){

	var showTrace = true;
	if($('#toggle-trace').is(':checked')){ showTrace = false; }
	var row = "";

	var head = buildJoblogHeader(data, site);
	$("#job-header-table").html(head);

	job_cache.timestamp = data.timestamp;
	job_cache.jobID = jobid;

	// update the system status entries on top
	jQuery.each(data.entries.systemEntries, function(i,e){
		var update=false;
		if($("#"+e.id).length){ 
			if(e.status == 3){  update=true; }					// they are in an active status, they want updates
			else{ if($("#"+e.id).attr("data-ts") != e.timestamp){ update=true; } }	// our data is new than theirs 
			if(!update){ return; }
		}

		var cls = "";
		var style = "";
		if(e.status == 3){ cls = "class='active_task'"; }
		else if(e.status == 4){
			cls ="class='trace'"; 
			if(showTrace == false){ style  = "style='display: none;'"; }
		}
		var row = "<tr "+cls+" "+style+" id='"+e.id+"'>";
                row +="<td nowrap width='10%'>" + getDate(e.timestamp) + "</td>";
                row +="<td width='10px;' ><span class='"+getStateClass(e.status)+"'>" + getJobLogEntryStatusDesc(e.status) + "</span></td>";
//                var msg = checkForKnownMessage(e) + e.description;
		let msg = e.description;
                row += "<td>" + msg + "</td></tr>";

		if(update){ $("#"+e.id).replaceWith(row); }	// drop and replace w/ new content
		else{ $("#joblog-systems").append(row); }	// new to us, add it
        });

	// update the progress bars for the VMs
        jQuery.each(data.Job.vmsInJob, function(i,vmd){
		var pb = makeVmPB(data.vmProgress[vmd.VMID], data.Job.status);		// we should check how many entries we have
		var st = makeVmPbStatus(data.vmProgress[vmd.VMID], data.Job.status);
		if(st.indexOf("Not Processed") !== -1 || st.indexOf("Not Started") !== -1){ 
			var guy = data.entries.vmEntries.find(obj => obj.id === vmd.VMID);
			if(guy != null){
				if(guy.entries.length > 1){ st = "[Pending]"; }	// the vm has started, but no progress has been recorded yet
				if(guy.entries.some(obj => obj.status === '1')){
					st = "[Errored]";	// we found an errored entry
				}
			}
		}
		$("#vmd_prog_st_"+ vmd.VMID).html(st);
		$("#vmd_prog_"+ vmd.VMID).html(pb);
	});

	// loop over each of the systems in the job, and add/update their entries
	jQuery.each(data.entries.vmEntries, function(i,vme){
		var ourTable = "vmd_"+vme.id;
		jQuery.each(vme.entries, function(i,e){
			var update=false;
			if($("#"+e.id).length){ 
				if(e.status == 3){  update=true; }					// they are in an active status, they want updates
				else{ if($("#"+e.id).attr("data-ts") != e.timestamp){ update=true; } }	// our data is new than theirs 
				if(!update){ return; }
			}
			var cls = "";
			var style = "";
			if(e.status == 3){ cls = "class='active_task'"; }
			else if(e.status == 4){
				cls ="class='trace'";
				if(showTrace == false){ style  = "style='display: none;'"; }
			}
			var row = "<tr id="+e.id+" "+cls+" "+style+" data-ts="+e.timestamp+">";
			row +="<td nowrap class='shrinkFit'>" + getDate(e.timestamp) + "</td>";
			row +="<td class='shrinkFit' ><span class='"+getStateClass(e.status)+"'>" + getJobLogEntryStatusDesc(e.status) + "</span></td>";
//			var msg = checkForKnownMessage(e) + e.description;
			let msg = e.description;
			row += "<td>" + msg + "</td></tr>";
			if(update){ $("#"+e.id).replaceWith(row); }	// drop and replace w/ new content
			else{ $("#"+ourTable).append(row); }	// new to us, add it
		});
	});

	// if we're still active, go back for another loop
        if(data.Job.status ==2 || data.Job.status ==3 || data.Job.status==0){ 
		doJLDRefresh(data.Job.jobID, site); 
	}
}

// this makes the per-vm block with their progress bars.  No details tables are made here
function buildJoblogVmHeader(data, vmd){
	var vmprog = data.vmProgress[vmd.VMID];
	
	var pb = makeVmPB(vmprog, data.Job.status);
	var st = makeVmPbStatus(vmprog, data.Job.status);
	var row = "<td class='shrinkFit align-middle'><i class='fas fa-angle-right fa-lg collapse-arrow'> </i> "+vmd.VMName+"</td>";
	row += "<td class='align-middle'> "+ pb +"</td>";
	row += "<td class='align-middle'> "+ st +" </td>";

	return row;
}
function makeVmPB(vmprog, jobStatus){
        var cls = "";
        if(vmprog >= 100){
                cls=  "bg-success";
        } else if(vmprog > 0){
                cls=  "bg-info";
        }else if(vmprog ==0){
                if(jobStatus == JobStatus.cancelled || jobStatus == JobStatus.failed){
                        cls = "bg-warning";
                        vmprog = 1;
                }
        }else if(vmprog < 0){   // negative progress means failure (and that it has ended)
                cls = "bg-danger";
                vmprog *= -1;
        }
	if(!isNumeric(vmprog)){ vmprog = 1; }
	return makePB(Math.round(vmprog), cls);	
}
function makeVmPbStatus(vmprog, jobStatus){
        var st = " ";
        if(vmprog >= 100){
                st = " [Completed] ";
        } else if(vmprog > 0){
                st = " [In Process] ";
        }else if(vmprog ==0){
                st = " [Not Started] ";
                if(jobStatus == JobStatus.cancelled || jobStatus == JobStatus.failed){
                        st = " [Not Processed] ";
                }
        }else if(vmprog < 0){   // negative progress means failure (and that it has ended)
                st = " [Failed] ";
        }
	return st;
}

// this is the top panel with basic job info and controls
function buildJoblogHeader(data, site){
	var name = data.Job.name;
	if(name.length > 32){
		name = name.substring(0,29);
		name += "...";
	}
	var panelBg = "bg-info";
	// 6 = complete, 4= warning, 7 = failed, 2=active
	var statDesc = getStatusDesc(data.Job.status);
//	if(data.Job.status != 2){
		let cls = ``;
		if(data.Job.status == 4){ cls = 'bg-warning'; }
		else if(data.Job.status == 8){ cls = 'bg-success'; }
		else if(data.Job.status == JobStatus.activeWithErrors){ cls = 'bg-active'; }
		else if(data.Job.status == JobStatus.active){ cls = 'bg-active'; }
		else if(data.Job.status == 6){ cls = 'bg-success'; }
		else if(data.Job.status == 7){ cls = 'bg-danger'; }
		statDesc = `<span class='badge ${cls}'>${statDesc}</span>`;
//	}
	var done = getDate(data.Job.timeEnd);
	var begin = getDate(data.Job.timeBegin);
	var runtimeLbl = "Run Length:";

	// for time so far:
	//  var now = new Date().getTime() / 1000;
	//  el = now - data.Job.timeBegin;
	
	
//	var end = data.Job.timeEnd;
//	if(end == 0){ end = Math.floor(new Date().getTime() / 1000); }
//	var el = end - data.Job.timeBegin;
	var el = data.Job.timeElapsed;
	var runtime = timeElapsed(el);
	// if this job has a throughput stat, use it instead (stored in the old username field)
	
	if(data.Job.username > 0) { el = data.Job.username; }
	else{
		// we need an estimate since the data's not in yet
		// if we don't the xfer stat slowly drops until another vm completes
		el = el + (el / data.Job.progress) * (100 - data.Job.progress);
	}
	var tots = prettySize(data.Job.originalSize);
	var xfer = prettyThroughput(data.protectedSize, el);
	if(data.protectedSize <=0){ xfer = "N/A"; }

	let finishedLbl = "Finished:";
	if(data.Job.status == 2 || data.Job.status ==3 || data.Job.status ==0){
		runtimeLbl = "Progress: ";
		finishedLbl = "Elapsed:";
		done = runtime;

		runtime = makePB(data.Job.progress, "bg-info");
		panelBg = "bg-success panel-active";
		statDesc += " <button class='btn btn-warning btn-xs job-cancel clickable' data-id='"+data.Job.jobID+"' data-site='"+site+"'>Cancel Job</button>"; 
	}else if(data.Job.status == 6){ panelBg = "bg-info panel-complete"; }
	else if(data.Job.status == 7){ panelBg = "bg-danger panel-error"; }
	else if(data.Job.status == 5){ panelBg = "bg-success panel-warning"; }	// success makes the text stand out better
	else if(data.Job.status == 0){ panelBg = "bg-secondary panel-inactive"; }

	if(data.Job.status >=4 && data.Job.type != 100 && data.Job.type != 101 && data.Job.type !=8 && data.Job.type !=10){
		statDesc += " <button class='btn btn-success btn-xs clickable schedule-run' data-id='"+data.Job.scheduleID+"' data-site='"+site+"'><i class='fas fa-sync-alt'></i> Re-run Job</i></button>"; 
	}

	let protLbl = "Total Protected:";
	if(data.Job.type == 1){
		protLbl = "Total Restored:";
	}
        let clicker = 'class="clickable schedule-edit far fa-calendar"';
        if(data.Job.type != JobType.restore && data.Job.type != JobType.backup){ clicker ='class="fas fa-cogs"'; }

	var pane = `<div class='container-fluid rounded ${panelBg}' id='job-header-table'><div class='row p-lg-2'' >
	<div class='col'><span ${clicker} data-type=${data.Job.type}' data-site=${site} data-id=${data.Job.scheduleID}> ${name}</span> </div>
	<div class='col'>Started: ${begin} </div>
	<div class='col'>${protLbl} ${tots} </div>
	<div class='w-100'></div>

	<div class='col'>Job Type: ${getJobTypeDesc(data.Job.type)} </div>
	<div class='col'>${finishedLbl} ${done} </div>
	<div class='col'>Job Throughput: ${xfer} </div>
	<div class='w-100'></div>

	<div class='col'>Status: ${statDesc} </div>
	<div class='col'>${runtimeLbl} ${runtime} </div>
	<div class='col'> </div>
	</div></div>`;

	return pane;
}


function doJLDRefresh(jobID, site){
	window.jldTimer =setTimeout(function() {getJobDetails(jobID, site ); }, 1000);
}

////////////////////////////////////////////////////// Instaboot Pages ///////////////////////////////////////////////////////////////
function drawInstaboots(data){
        setupPage(" <a href='#' class='go_insta'>InstaBoots</a>", "InstaBoots");
        $("#dataTableDiv").append("<div id='dataTable'></div>");
	if(!checkNoA3s()){ return; }
        if(data.A3s.length==0){
                var row = "<div class='row bg-info m-2'><div class='col-12 panel-inactive rounded align-middle p-4' style='text-align:center;'><h3 >There are no A3s defined yet</h3></div></div>";
                $("#dataTable").append(row);
		finishLineLoad();
                return;
        }

        jQuery.each(data.A3s, function(i,a3){
                var row = "<div class='row bg-info m-2 ";
                var icon = "";
                if(data.A3s.length > 1){
                        row += "expando clickable collapse-arrow";
                        icon = "<i class='fas fa-angle-right' fa-lg'> </i>";

                }
                row += "'><div class='col-12 panel-complete rounded align-middle p-2'><h5 > "+icon+" InstaBoot details for: "+a3.name+"</h5></div></div>";
                row += "<div id='a3-"+a3.id+"' class='container-fluid'>Loading InstaBoot details from "+a3.name+"... </div>";
                row += "</div>";
                $("#dataTable").append(row);
                wsCall("listInstaboots",null,"populateA3Instaboots",a3.id, a3.id);
        });
        $("#dataTable").append(row);
}

function populateA3Instaboots(data, site){
	var us = $("#a3-"+site);
	if(data.result != "success"){
		var barf = "<div class='callout callout-danger'><h5>Error retrieving Instaboot details from A3</h5><p>"+data.message+"</p></div>";
		us.html(barf);
		return;
	}
	var row = "";
	var cnt =site * 1000;
//////////////////
	var stuff = "<div class='row p-2'><br> <div class='col-md-10 col-sm-12'>";
        stuff += "<br><h4 style='text-align: center;'>VMs running from Alike Storage</h4>";

	us.html('');
        var hasAnyStuff = false;
        if(data.VMs.length >0){
                hasAnyStuff = true;
                changedSize = 0;
                data.VMs.forEach(i => {
                        if(i.power == "running") {
                                powerState = "2";
                        } else if(i.power == "halted") {
                                powerState = "0";
                        } else if(i.power == "paused") {
                                powerState = "3";
                        } else if(i.power == "suspended") {
                                powerState = "3";
                        } else {
                                powerState = "666";
                        }
			let deltaTip = printTip("The total actual additional space consumed by this disk, on the Alike SR");

                        myState = getPowerState(powerState);
                        changedSize = 0;
                        i.disks.forEach(d => {changedSize += d.changedBytes;});
                        myTable =       `<table id='diskStuff' class='details_table border-grey' width=500>
                                        <tr><th>Disk</th><th>Original Size</th><th>Accrued changes (delta)${deltaTip}</th></tr>`;
                        diskPos = 0;
                        i.disks.forEach(d => {
                                myTable +=  `<tr><td><img src='/images/drive-icon.png' 
                                                alt='Drive ${diskPos +1}'>${diskPos +1}</td>
                                        <td>${prettySize(i.meta.VBDS[diskPos].size)}</td>`;
                                myTable += `<td>${prettySize(d.changedBytes)}</td></td></tr>`;
                                diskPos++;
                        });
                        myTable += `</table>`
                        changedSize = prettySize(changedSize);
                        cleanDate = getDateCust(i.bootDate, true);
                        stuff+=`
                                <div class='row'>
                        <div class='card border-secondary'>
                        <div class='card-header'>Details for: ${i.meta.name}</div>
                        <div class="card-body">
                                                        <div class="row">
                                                                <div class="col-sm-2">VM:</div> 
                                                                <div class="col-sm-10">${i.meta.name}  (${i.meta.description}) </div>

                                                                <div class="col-sm-2">Power State:</div> 
                                                                <div class="col-sm-10">${i.power} ${myState} </div>

                                                                <div class="col-sm-2">UUID:</div> 
                                                                <div class="col-sm-10">${i.vm} </div>
                                                                <div class="col-sm-2">Running on Host:</div>
                                                                <div class="col-sm-10"><img src="/images/xeniconblue.png" alt="XenServer">${i.host}</div>
                                                                <div class="col-sm-2">Backup From:</div>
                                                                <div class="col-sm-10">${cleanDate}</div>
                                                                <div class="col-sm-2">Changed Data:</div>
                                                                <div class="col-sm-10">${changedSize}</div>
                                                                <div class="col-sm-12">Active Disk Info:<br>${myTable}</div>
                                                        </div>
                                                </div>
                                                </div>
                                        </div>
                                </div>`;
                });
        } else {
                stuff += `<br>`;
		stuff += "<h4 style='text-align: center;'>No instaboots started yet</h4>";
                stuff += `</div>`;
        }

	var panelBody = ` <div id="pie_parent"><canvas id="sr_pie_guy-${site}"></canvas></div><br> 
				<table><tr><td width='100px;'><span id='free-tag-${site}'>Free</span></td><td>`;
	panelBody += prettySize(data.SR.free) +"</td></tr>";
	panelBody += "<tr><td><span id='used-tag-"+site+"'>A3 Used</span></td><td>"+prettySize(data.SR.used)+"</td></tr>";
	panelBody += "<tr><td><span id='other-tag-"+site+"'>Other Used</span></td><td>"+ prettySize((data.SR.total - data.SR.free) - data.SR.used)+"</td></tr>";

	stuff += "<div class='col-2'>";
	stuff += printPanel("Instaboot & SR Usage", panelBody, "card-secondary")
	stuff += "</div>";
        us.html(stuff);

	var colors = drawDisk3Pie(data.SR.total, data.SR.free, data.SR.used, "sr_pie_guy-"+site, true);
	$("#free-tag-"+site).css('color', colors.free);
	$("#used-tag-"+site).css('color', colors.used);
	$("#other-tag-"+site).css('color', colors.other);
	lastSRInfo = data.SR;



//////////////////
        finishLineLoad();
        renderTips();
}

////////////////////////////////////////////////////// EndInstaboot Pages ///////////////////////////////////////////////////////////////

////////////////////////////////////////////////////// ABD Pages ///////////////////////////////////////////////////////////////
function drawABDsTable(data) {
        setupPage(" <a href='#' class='go_abds'>ABDs</a>", "ABDs");
        $("#dataTableDiv").append("<div id='dataTable'></div>");
	if(!checkNoA3s()){ return; }
        if(data.A3s.length==0){
                var row = "<div class='row bg-info m-2'><div class='col-12 panel-inactive rounded align-middle p-4' style='text-align:center;'><h3 >There are no A3s defined yet</h3></div></div>";
                $("#dataTable").append(row);
                return;
        }
        jQuery.each(data.A3s, function(i,a3){
                var row = "<div class='row bg-info m-2 ";
		var icon = "";
                if(data.A3s.length > 1){
                        row += "expando clickable collapse-arrow";
			icon = "<i class='fas fa-angle-right' fa-lg'> </i>";
			
                }
                row += "'><div class='col-12 panel-complete rounded align-middle p-2'><h5 > "+icon+" ABDs details for: "+a3.name+"</h5></div></div>";
		row += "<div id='a3-"+a3.id+"' class='row m-3'>Loading ABD details from "+a3.name+"... </div>";
		row += "</div>";
                $("#dataTable").append(row);
		wsCall("abds",null,"populateA3ABDs",a3.id, a3.id);
        });
        $("#dataTable").append(row);
}

function populateA3ABDs(data, site){
	var us = $("#a3-"+site);
	if(data.result != "success"){
		var barf = "<div class='callout callout-danger'><h5>Error retrieving ABD information from A3</h5><p>"+data.message+"</p></div>";;
		us.html(barf);
		return;
	}
	var row = "";
	var cnt =site * 1000;

        jQuery.each(data.Pools, function(i,p){
		let sid = 'a3-'+site+"-"+cnt;
		let bod = '';

		if(p.ABDImage.uuid){
			let itip = printTip("Template UUID: <br><b>"+p.ABDImage.uuid+"</b>");
			let ttip = `<center><b>ABD Image Deployed ${itip}</b><br>`;
			impClass = 'btn-secondary';
			bod = ` ${ttip}
                        <button class='btn-info abd-diag' data-site="${site}" data-pool='${p.uuid}'><i class='fas fa-user-md' fa-lg'> </i> Diag </button>
                        <button class='btn-warning abd-cull-idle' data-site="${site}" data-pool='${p.uuid}' data-toggle='tooltip' title='Shutdown and remove all Idle ABDs'>
                                <i class='fas fa-snowplow' fa-lg'> </i> Cull </button>
			`;
		}else{
			let impButTip = 'Attempt to re-import the ABD image for this Xen Pool';
			let ttip = '<center><b>ABD Image not found! <i class="fas fa-exclamation-triangle" data-html="true" data-toggle="tooltip" title="ABD Template images are deployed automatically to a xen pool when they are first added.  <br><br>This situation is not normal, and you must either re-deploy the image manually, or reboot the A3 for it to automatically attempt to re-deploy the AMD image."></i></b><br>';
			bod = `${ttip}
                        <button data-toggle='tooltip' title='${impButTip}' class='btn-danger abd-import' data-site="${site}" data-pool='${p.uuid}'><i class='fas fa-pump-medical' fa-lg'> </i> Import </button>
`;

		}

		row += `<div class='container-fluid'>
			<div class='row m-1' id='${sid}' data-pool='${p.uuid}'>

			<div class='col-2'>
			<div class='card card-primary'>
			<div class='card-header'><h3 class='card-title'>${p.name}</h3></div>
			<div class='card-body' >
			${bod}
			</div>
		</div>
		</div>`;

		var netID = "";
		jQuery.each(p.ABDNets, function(i,n){ netID = n.netID; });
		row += "<div class='col-6'>";

		row += "<div class='input-group p-2 '><div class='input-group-prepend'>  <span class='input-group-text'>Xen Net:</span></div>";
                row += "<select class='form-control rounded-0 abd-vnetwork' id='xen-net-"+p.uuid+"' data-site='"+site+"' data-cnt="+cnt+">";
		jQuery.each(p.vNetworks, function(i,n){
			var sel = "";
			if(n.uuid == netID){ sel = "selected"; }
			row += "<option value='"+n.uuid+"' "+sel+"> "+n.name+" </option>";
		});
		row += "</select></div>";

		row += "<div class='container'> ";

		p.ABDNets = p.ABDNets.filter(obj => obj.ip !== "0.0.0.0");
		var checked = "checked";
		var hide = "style='display: none;'";
		if (p.ABDNets.length > 0){ checked = ""; hide= "";}

		var dhcp = "<div class='form-group'> <div class='custom-control custom-switch'>";
		dhcp += " <input type='checkbox' "+checked+" class='custom-control-input abd-use-dhcp' id='"+cnt+"' data-cnt="+cnt+" data-site="+site+" >";
		dhcp += "<label class='custom-control-label' for='"+cnt+"'>Use DHCP</label>";
		dhcp += " </div> </div> ";

		row += dhcp;
		row += "</div>";


		row += "<div id='manual-ip-"+cnt+"' "+hide+">";

		row += "<div class='row m-2'>";
		row += "<div class='col-6'>";

		row += "<table>";
		row += "<tr><td> <div class='input-group'>  <div class='input-group-prepend'>  <span class='input-group-text'>IP</span>";
		row += "</div> <input type='text' class='form-control abd-ip' data-inputmask=\"'alias': 'ip'\" data-mask inputmode='decimal' placeholder='xxx.xxx.xxx.xxx'>";
                row += "<select class='form-control rounded-0 abd-ip-mask' id='ip-subnet-"+p.uuid+"'>";
		for (let i = 9; i < 31; i++) {
			let sel=""
			if (i == 24){ sel = "selected"; }
			row += "<option value="+i+" "+sel+">/"+i+"</option>";
		} 	
		row += "</div></td></tr>";
		row += "<tr><td>  <div class='input-group'><div class='input-group-prepend'>  <span class='input-group-text'>GW</span>";
                row += "</div><input type='text' class='form-control abd-gw' placeholder='xxx.xxx.xxx.y'></div> </td></tr>";
		row += "<tr><td align='right'><button class='btn btn-sm btn-info abd-ip-add' data-site="+site+" data-cnt="+cnt+">Add</button></td>";
		row += "</tr></table>";

		row += "</div><div class='col-6'>";

		row += "<table id='abd-ips-"+cnt+"' ><tr><td><h5>Assigned Addresses:</h5></td></tr>";
		// p.ABDNets[ID, MAC, checkedOut, dns, gateway, id, ip, netID, netmask, poolID, publicAddress]
		jQuery.each(p.ABDNets, function(i,n){
			row += makeAbdNetRow(n.ip, n.netmask, n.gateway, cnt, n.ID, site);
		});
		row += "</table>";
		row += "</div>";

		row += "</div>"; // end of manuel IPs
		row += "</div>"; // end of A3 pool right panel
		row += "</div>"; // end of pool stanza

		cnt++;	// counter per-pool for checkboxes
		row += `<div class='col-4'> 
				<div class='row' style='line-height:100px';> </div>
				<div class='row'> 
					<table>
					<tr><td colspan=3><h5>Active ABDs</h5></td></tr>`;
		$.each(p.ABDs, function(i, a){
				let use = `Idle <i class="fas fa-minus-circle abd-cull" style='color: red;' data-site=${site} data-toggle='tooltip' title='Delete this ABD' data-id=${a.ID}></i>`;
				if(a.vmOwner > 0){ use = "In use"; }
				row += `<tr><td><b>${a.name}</b> - </td><td>[${a.ip}] </td><td> -${use}</td></tr>`;
		});
		if(p.ABDs.length ==0){
			row += `<tr><td colspan =3>No running ABDs found</td></tr>`;
		}
		row += `		</table>
				</div>
			</div>`;

		row += "</div><hr>";

	});

	us.html(row);

	finishLineLoad();
	renderTips();
}
function makeAbdNetRow(ip, mask, gw, cnt, id, site){
	return "<tr><td><span style='font-size:12px;'>"+ip+" /"+ maskToCIDR(mask) +" [GW: "+gw+"] <i class='clickable fas fa-trash abd-ip-del fa-lg' data-cnt="+cnt+" data-id="+id+" data-site="+site+" data-toggle='tooltip' title='Remove this IP'></i> </span></td></tr>";

}
////////////////////////////////////////////////////// End ABD Pages ///////////////////////////////////////////////////////////////



function drawBackupsTable(data, dsSite=0) {
	showLineLoad();
	if(dsSite==0){
		setupPage(" <a href='#' class='go_backup-hist'>Backup History</a>", "Backup History");
	}else{
		setupPage(" <a href='#' class='go_vault-hist'>Vault History</a>", "Vault History");
	}
        $("#dataTableDiv").append("<div id='dataTable'></div>");

	if(!checkNoA3s()){ return; }
        if(data.A3s.length ==0){
                var row = "<div class='row bg-info m-2'><div class='col-12 panel-inactive rounded align-middle p-4' style='text-align:center;'><h3 >There are no backups</h3></div></div>";
                $("#dataTable").append(row);
		return;
        }

	setupBackupTable(dsSite);

        jQuery.each(data.A3s, function(i,a){
		if(dsSite == 0){
			wsCall("backups",null,"populateBackupTable",a.id, a.id);
		}else{
			wsCall("vaults",null,"populateVaultTable",a.id, a.id);
		}
	});
}

function setupBackupTable(dsSite){
        var row = "<table class='table table-bordered table-hover table-striped' id='backup-table' style='width:100%'>";
	if(dsSite==0){
		row += `<thead><tr>
				<th></th>
				<th style='width: 110px;'>Actions</th>
				<th>System Name</th>
				<th>Date</th>
				<th>Managing A3</th>
				<th>Total Size</th>
				<th>Stored</th>
				<th>Time</th>
				<th>Sites</th>
				<th>Retention</th>
				<th>Trash</th>
			</tr></thead><tbody></table>`
	}else if(dsSite ==1){
		row += `<thead><tr>
				<th></th>
				<th style='width: 100px;'>Actions</th>
				<th>System Name</th>
				<th>Date</th>
				<th>Managing A3</th>
				<th>Total Size</th>
				<th>Stored</th>
				<th>Time</th>
				<th>Trash</th>
			</tr></thead><tbody></table>`;
	}

	$("#dataTable").append(row);


        var table = $("#backup-table").DataTable({
                pageLength: 20, 
                lengthMenu: [5, 10,15, 20, 50, 100, 200, 500],
                language: {
                      lengthMenu: "Display _MENU_",
                },
                buttons: [
                    {
                        extend:    'copyHtml5',
                        className: 'btn-outline-info',
                        text:      '<i class="fas fa-copy"></i>',
                        titleAttr: 'Copy'
                    },
                    {
                        extend:    'excelHtml5',
                        className: 'btn-outline-info',
                        text:      '<i class="fas fa-file-excel" style="color:#7A9673;"></i>',
                        titleAttr: 'Excel'
                    },
                    {
                        extend:    'csvHtml5',
                        className: 'btn-outline-info',
                        text:      '<i class="fas fa-file-alt" style="color:#758B9E;"></i>',
                        titleAttr: 'CSV'
                    },
                    {
                        extend:    'pdfHtml5',
                        className: 'btn-outline-info',
                        text:      '<i class="fas fa-file-pdf" style="color:#F4A9A9;"></i>',
                        titleAttr: 'PDF'
                    }
                ]
        }).buttons().container().prependTo('#page-footer')


	adjustTableNav($("#backup-table_wrapper"));

        $('.buttons-html5').each(function() {
                $(this).removeClass('btn-secondary');
        });
	renderTips();
        var table = $('#backup-table').DataTable();
	if(isManager()){
		table.column( 4 ).visible( false );
	}
}

function populateBackupTable(data, site){
	let a3Name = getA3FromId(site);
	var ds=0;
	if(data.vaults ==1){ ds =1; }
	var table = $('#backup-table').DataTable();
        jQuery.each(data.VmVersions, function(i,v){
		v.isOffsite = parseInt(v.isOffsite);
		v.isOnsite = parseInt(v.isOnsite);
		var result = [];
		result.push(v.timestamp);
		var rest = `<i class='fas fa-upload clickable quick-restore' style='color: #1e050;' data-toggle='tooltip' title='Full Restore to Xen or Hyper-v' data-name='${v.name}'data-uuid='${v.uuid}' data-ts='${v.timestamp}' data-site=${site} data-ds=${ds}></i> &nbsp;
		<i class='fas fa-folder-open clickable flr-browse-version' style='color: #f5c211;' data-toggle='tooltip' title='Browse Files and Folders' data-ts=${v.timestamp} data-uuid='${v.uuid}' data-ds=${ds} data-site=${site}></i> &nbsp;
		<i class='far fa-check-circle clickable validate-version' style='color: #91c97d;' data-toggle='tooltip' title='Run validation job on backup' data-ts=${v.timestamp} data-uuid='${v.uuid}' data-ds=${ds} data-site=${site}></i>
		`;

		let dt = getDate(v.timestamp, true);
		if(v.jobID > 0){
			dt = `<span class='go_joblog clickable' data-id=${v.jobID} data-site=${site}><i class='fas fa-list-ul' style='color: #11aaf5;' data-toggle='tooltip' title='View Backup Job Log'> </i> ${dt}</span>`;
		}
		let name = `<span class='clickable view-vm-details' data-uuid='${v.uuid}' data-site=${site}> ${v.name}</span>`;
		result.push(rest);
		result.push(name);
		result.push(dt);
		result.push(a3Name.name);
		result.push(prettySize(v.totalSize) );
		result.push(prettySize(v.dedup) );
		result.push(timeElapsed(v.processingTime) );
		var sites= "";
		if(v.isOffsite){ sites = "<span class='badge bg-primary'>Offsite</span>"; }
		else{ 
			sites = `<i class='fas fa-cloud-upload-alt clickable vault-backup' data-toggle='tooltip' title='Send Backup to Offsite Vault' data-site='${site}' data-ts='${v.timestamp}' data-uuid='${v.uuid}'></i>`;
		}
		result.push(sites);
		result.push(v.gfsPolicyOn);
                var trash = `<i class='fas fa-trash clickable delete-backup' data-toggle='tooltip' title='Delete Backup' data-uuid='${v.uuid}' data-ts='${v.timestamp}' data-site=${site} data-ds=${ds} ></i>`;
		result.push(trash);
		table.row.add(result);
	});

	table.column( 0 ).visible( false );

        if(window.nodeMode ==2){ table.column( 4).visible( false ); }

	
	// if not DR, hide this column	
	//table.column( 8 ).visible( false );

	table.order([0, 'desc']);
	table.draw();
	finishLineLoad();

	renderTips();
}

function populateVaultTable(data, site){
	var ds=1;
        var table = $('#backup-table').DataTable();

	if(data.odsStatus ==0){
		let note = "<br><center><h3>Offsite Vaulting is not configured</h3>No ODS has been defined.</center><br>";
		$('#backup-table').html(note);
		return;
	}

        jQuery.each(data.VmVersions, function(i,v){
		v.isOffsite = parseInt(v.isOffsite);
		v.isOnsite = parseInt(v.isOnsite);
                var result = [];
                result.push(v.timestamp);
                var rest = "<i class='fas fa-level-up-alt clickable' data-toggle='tooltip' title='Full Restore (from Vault)'></i>";
		var rest = `<i class='fas fa-upload clickable quick-restore' style='color: #1e050;' data-toggle='tooltip' title='Full Restore (from Vault)' data-name='${v.name}' data-uuid='${v.uuid}' data-ts='${v.timestamp}' data-site=${site} data-ds=${ds}></i> &nbsp;
		<i class='fas fa-folder-open clickable flr-browse-version' style='color: #f5c211;' data-toggle='tooltip' title='Browse Files and Folders' data-ts=${v.timestamp} data-uuid='${v.uuid}' data-ds=${ds} data-site=${site}></i> `;
		if(!v.isOnsite == 1){
			rest += ` &nbsp; <i class='fas fa-cloud-download-alt clickable reverse-vault' data-toggle='tooltip' title='Download Backup From Vault to ADS' data-site='${site}' data-ts='${v.timestamp}' data-uuid='${v.uuid}'></i>`;
//			rest += " &nbsp; <i class='fas fa-cloud-download-alt clickable reverse-vault' data-toggle='tooltip' title='Copy Backup to Local ADS'></i>";
		}
                result.push(rest);
                result.push(v.name);
                result.push(getDate(v.timestamp, true));
                result.push("A3 name");
                result.push(prettySize(v.totalSize) );
                result.push(prettySize(v.dedup) );
                result.push(timeElapsed(v.processingTime) );
                var trash = `<i class='fas fa-trash clickable delete-backup' data-toggle='tooltip' title='Delete Vault' data-uuid='${v.uuid}' data-ts='${v.timestamp}' data-site=${site} data-ds=${ds} ></i>`;
                result.push(trash);
                table.row.add(result);
        });
        table.column( 0 ).visible( false );
        if(window.nodeMode ==2){ table.column( 4).visible( false ); }
        table.order([0, 'desc']);
        table.draw();
        finishLineLoad();
}

//////////////////////////////// VM Details 
function drawVmDetails(data, uuid) {
        showLineLoad();

	setupPage(" <a href='#' class='view-vm-details' data-uuid='"+uuid+"'>System Details</a>", "System Details");
        
        $("#dataTableDiv").append("<div id='dataTable'></div>");

	var panel = "<div class='container-fluid' id='vmdetails-panel'><center><h4>System Details for "+uuid+"</h4></div>";

        var args= new Object();
        args["uuid"] = uuid;
	wsCall("vm/details", args, "drawVmDetailHeader",null );

	// TODO: Alike DR only
	var chk = "";
	let lbl = "Showing Onsite Backups";
	if(data.vaults ==1){ 
		chk = "checked"; 
		lbl = "Showing Offsite Vaults";
	}
//	panel += `<div class='container-fluid'>
	panel += `<div class='col-1' style='top:40px;'>
		<div class="toggle-button" id="ds-toggle-button">
			<input type="checkbox" ${chk} class="flr-ds-checkbox view-vm-vaults" id='vm-vaults' data-uuid=${uuid} >
			<div class="knobs"> <span></span> </div>
			<div class="layer"></div>
		</div>
<!--		<div class='form-group'> <div class='custom-control custom-switch custom-switch-off-info custom-switch-on-success'>
		<input type='checkbox' ${chk} class='custom-control-input view-vm-vaults' id='vm-vaults' data-uuid=${uuid}  >
		<label class='custom-control-label' for='vm-vaults'>${lbl}</label>
		</div> </div> -->
	</div>`;

        $("#dataTable").append(panel);
        if(data.A3s.length ==0){
                var row = "<div class='row bg-info m-2'><div class='col-12 panel-inactive rounded align-middle p-4' style='text-align:center;'><h3 >There are no backups</h3></div></div>";
                $("#dataTable").append(row);
                return;
        }

	let dsSite = 0;
	if(data.vaults ==1){ dsSite =1; }

	setupBackupTable(dsSite);

        jQuery.each(data.A3s, function(i,a){                
		var args= new Object();
		args["uuid"] = uuid;
		if(dsSite ==1){
			wsCall("vaults", args, "populateVaultTable", a.id, a.id);
		}else{
			wsCall("backups", args, "populateBackupTable", a.id, a.id);
		}

        });

}

function drawVmDetailHeader(data){
	var vm = data.VM;
	var agent= null;
	var haveAgent = null;
        var autoip = "";
	if(vm.authProfile !== null && vm.authProfile != "" &&  $.trim(vm.authProfile) !== ""){
		let vers = ''
                 try{
			agent =JSON.parse(data.VM.authProfile); 
			autoip = " [<b>"+ agent.ip +"</b>]";
			vers = `(${agent.version.split(' ')[0]})`;
		 }catch(err){ agent=null;}
                haveAgent = `<span class='badge bg-success'>Detected ${vers}<span>`;
        }else{
                haveAgent = "<span class='badge bg-warning'>No QS Agent detected<span>";
	}

	var psz = prettySize(vm.totalSize);
	if(vm.totalSize <=0){ psz = "Size N/A"; }
	let vtype = getVirtTypeIcon(vm.type);
	if(vm.hostUUID == null || vm.hostUUID == ""){ vtype += " [Location unknown]"; }
	else if(vm.type == 10){
		vtype += " [Physical Agent] ";
	}
	else{ 
		vtype += " [On "+getNameFromCache(vm.hostUUID)+"]";
	}
	let accessIP = "Auto-detect";
	if(vm.accessIP != null && vm.accessIP != ""){ accessIP = vm.accessIP; }
	let maxOn = "Global Default";
	let maxOff = "Global Default";
	if(vm.maxOnsite != null){ maxOn = vm.maxOnsite; }
	if(vm.maxOffsite != null){ maxOff = vm.maxOffsite; }
	let lastBackup = "No Backups taken";
	if(vm.lastSuccess > 0){
		lastBackup = timeSince(vm.lastSuccess);
	}
	if(vm.hostUUID != null){
		if(vm.type == 10 && vm.poolID != 'licensed'){
		lastBackup = lastBackup +` <span class='badge bg-yellow quick-backup-nolic clickable' data-uuid=${vm.guid} data-a3id=${vm.a3id}>Take Quick Backup</button>`;
		}else{
		lastBackup = lastBackup +` <span class='badge bg-yellow quick-backup clickable' data-uuid=${vm.guid} data-a3id=${vm.a3id}>Take Quick Backup</button>`;
		}
	}

	let guidTip = `<i class='fas fa-question-circle copy-guid' title='UUID: ${vm.guid} (Click to copy)' data-guid='${vm.guid}' data-toggle='tooltip' data-html='true'></i>`;

	let agentType = 'QHB Agent';
	if(vm.type == 10){ agentType = 'Full Agent'; }

	var panelBg = "bg-info panel-complete";
        var pane = `<div class='container-fluid rounded ${panelBg}' id='job-header-table'><div class='row p-lg-2'' >

	<input type='hidden' id='agentuuid' value=${vm.guid} >
	<div class='col'>
        <div class='col'>Name: ${vm.name}  ${guidTip}</div>
        <div class='col'>Type: ${vtype}</div>
        <div class='col'>Detected IP: ${vm.ipaddress} </div>
	</div>

	<div class='col'>
        <div class='col'>Max Versions: <span id='vm-max-on'>${maxOn}</span> <i class='fas fa-edit fa-sm vm-max-on-edit' data-guid=${vm.guid}></i></div>
        <div class='col'>Max Offsite: <span id='vm-max-off'>${maxOff}</span> <i class='fas fa-edit fa-sm vm-max-off-edit' data-guid=${vm.guid}></i> </div>
        <div class='col'>Access IP: <span id='vm-accessip'>${accessIP}</span> <i class='fas fa-edit fa-sm vm-accessip-edit' data-guid=${vm.guid}></i></div>
	</div>

	<div class='col'>
        <div class='col'>Last Backup: ${lastBackup} </div>
        <div class='col'>Total Size: ${psz} </div>
        <div class='col'>${agentType}: ${haveAgent}  </div>
        </div>

	</div></div>`;	// end p-lg-2

	let a3Drop = '';
	if(vm.type == 10){
		if(window.nodeMode == 2){
			// owned by our only node, us!
		}else{
			let tip = printTip('Assigning an agent to an A3 will make it available to that A3 for protection.  The assigned A3 will also be responsible for enumeration of the agent.  For more details, please refer to the KB for more details on agent assignment.');
			a3Drop = `<div class='form-group' style='width:200px;'>${tip} Assigned to: 
					<select class='form-control' id='a3-agent-owner'>`;
			var a3s = JSON.parse(sessionStorage.getItem("a3Cache"));
			$.each(a3s, function(i, a){
				let sel = (a.id === vm.a3id) ? "selected" : "";
				a3Drop += `<option value=${a.id} ${sel}>${a.name}</option>`;
			});

			a3Drop += `</select> </div>`;
		}
	}
	if(agent != null){
		pane += `<div class='container-fluid rounded bg-info' ><div class='row p-lg-2'' >
		<div class='col p-2'>Agent Version: ${agent.version.split(' ')[0]}
		<br>Uptime: &nbsp; ${formatHours(agent.uptimeHours)}
		<br>Install Date: &nbsp; ${agent.instdate}
		<br>Last seen: ${timeSince(agent.lastCheck)} </div>

		<div class='col'> 
			${a3Drop}
			<button class='btn btn-success btn-sm test-agent' data-site='${vm.a3id}' data-uuid='${vm.guid}'>Test Agent</button>
		</div>
		<div class='col p-1'><div id='agent-test-results'></div> 
		</div> <br>`;
	}
	
	$("#vmdetails-panel").html(pane);

        $("#a3-agent-owner").on("change", function() {
		var args = {};	
		args["uuid"] = $("#agentuuid").val();
		args["a3id"] = $("#a3-agent-owner").val();
		wsCall("agent/assign", args, 0);  // settings gets them all, setting sets one or more
	});
}

function agentChangeCB(data){
	if(data.result == "success"){	
		doToast("Agent re-assigned successfully", "Agent Assigned", "success");
	}else{
		doToast(data.message, "Failed to assign Agent", "warning");
	}
}

function displayAgentResults(res){
        $("#agent-test-results").html("");
        $("#agent-test-results").html("Agent Results:");
        $("#agent-test-results").removeClass();
	if(res.thing == "success"){
		$("#agent-test-results").addClass("bg-success p-2 rounded");
		$("#agent-test-results").html(res.message);
	}else{
		$("#agent-test-results").addClass("bg-warning p-2 p-2 rounded");
		$("#agent-test-results").html(res.message);
	}
}
function drawSupport(){
        setupPage(" <a href='#' class='go_support'>Support</a>", "Support");
        $("#dataTableDiv").append("<div id='dataTable' class='p-3'></div>");
	let r = "";

	r =`        
		<div class='row'>
			<div class='col p-3 text-center'>
			<h4><i class='fas fa-ambulance'></i> Documentation, KB Articles, and Tech Support Resources</h4>
			</div>
		</div>
		<div class='row p-3'>
			<div class='col-1'></div>
			<div class='col-5'>
				    <div class="small-box bg-info qs_kb_link clickable">
				      <div class="inner">
					<h3>Quadric KB</h3>
					<p>Find support articles and guides</p>
				      </div>
				      <div class="icon"> <i class="fas fa-sitemap"></i> </div>
					<a href="#" class="small-box-footer"> Open External <i class="fas fa-arrow-circle-right"></i> </a>
				      </div>
			</div>

			<div class='col-5'>
				    <div class="small-box bg-lightblue qs_admin_guide_link clickable">
					      <div class="inner">
						<h3>A3 Admin Guide</h3>
						<p>The A3 manual</p>
					      </div>
					      <div class="icon"> <i class="fas fa-indent"></i> </div>
				      <a href="#" class="small-box-footer"> Open External <i class="fas fa-arrow-circle-right"></i> </a>
				    </div>
			</div>
			<div class='col-1'></div>
		</div>

                <div class='row'>
                        <div class='col-3'></div>
                        <div class='col-5'>
                                    <div class="small-box bg-success qs_support_link clickable" id='tech-support-box'>
                                      <div class="inner">
                                        <h3>Support Desk</h3>
                                        <p>Get help from our experienced support team</p>
                                      </div>
                                      <div class="icon"> <i class="fas fa-life-ring"></i> </div>
                                        <a href="#" class="small-box-footer"> Open External <i class="fas fa-arrow-circle-right"></i> </a>
                                      </div>
                        </div>

                        <div class='col-1'></div>
                </div>


		</div> 
	`;

        $("#dataTable").append(r);
	renderTips();
}

function drawSubscription(data){
	let sets = data.settings;
	let sub = data.sub;
        setupPage(" <a href='#' class='go_subscription'>Subscription</a>", "Subscription");
        $("#dataTableDiv").append("<div id='dataTable' class='p-3'></div>");

	let subInf = "";
	if(sub.edition == -1){
		subInf = `
			No Active Subscription found!
			<br>You can add this A3 to your plan from the 
			<button class='btn btn-sm btn-info qs_portal_link'><i class='fas fa-external-link-alt'></i> QS Portal</button><br><br>

			If this A3 is already in a plan, this could be due to connectivity errors 
			with the A3 reaching the internet.<br>
		`;

	}else{

		let edition = "Alike DR";
		if(sub.edition ==1){ edition  = "Alike Standard"; }
		let suffixes = ['th', 'st', 'nd', 'rd', 'th', 'th', 'th', 'th', 'th', 'th'];
		let date = new Date(new Date().getFullYear(), 0); 
		date.setDate(sub.billday); 
		let day = date.getDate();
		let suf = suffixes[day % 10] || 'th';
		let billDate = date.toLocaleString('default', { month: 'short' }) +", "+ day +suf;

		let subStatus = getSubStatusStr(sub.status);
		let planType = "Socket-based";

		let ownerTip = printTip('Only this account can manage the subscription and open support tickets in the Portal.<br><br>If the Portal "Restrict Access" option is set, then only this account can login to this A3 as well.');
		subInf = `
			Alike Edition: ${edition}<br>
			Total Plan Licenses: ${sub.totalSockets}<br>
			Plan Status: ${subStatus}<br>	
			Bill Date: ${billDate}<br>
			Org. Name: ${sub.company}<br>
			Plan Name: ${sub.subName}<br>
			Plan Owner: ${sub.ownerName} (${sub.ownerEmail}) ${ownerTip}<br>
			<br><center>
				<button class='btn btn-sm btn-success qs_portal_link'><i class='fas fa-external-link-alt'></i> Manage in the QS Portal</button>
			</center>
		`;
	}
	let mode = "Full Stack";
	if(data.mode ==0){ mode = "A3 Manager"; }

	let a3Inf = `
		A3 Name: ${sub.a3Name}<br>
		A3 GUID: ${data.a3guid}<br>
		A3 Build: ${data.a3build}<br>
		A3 Mode: ${mode} [${data.mode}]<br>
		Licenses Granted to this A3: ${sub.sockets}<br>	
	`;

	let but = `<i class='fas fa-sync-alt refresh-sub-info' title='Refresh Sub information from Quadric Webservices' data-toggle='tooltip' data-html='true'></i>`;
	let p = printPanelEx("A3 Plan Details", subInf, "card-primary",but);
	let a =printPanel("Local A3 Details", a3Inf, "card-secondary"); 

	let r = `
		<div class='row'>
			<div class='col'>
			${p}
			</div>
			<div class='col'>
			${a}
			</div>
		</div>
	`;
        $("#dataTable").append(r);
	renderTips();
}

function drawSettings(data) {

	// TODO: we should know if we're a fullstack or stand alone.
	// 	We need this b/c we should HIDE/disable managerIP on fullstack and REPLACE it with hostIP

	var sets = data.settings;
	var managerIP = "";
	if('managerIP' in sets){ managerIP = sets.managerIP; }

        setupPage(" <a href='#' class='go_settings'>Settings</a>", "Settings");
        $("#dataTableDiv").append("<div id='dataTable' class='p-3'></div>");

	let manageIPtip = printTip('The IP remote agents and A3 nodes will use to connect to this system.');
	let sessTip =printTip('Timeout in seconds for web sessions.');
	let webSockTip = printTip('Timeout in seconds for all webservice connetion attempts. <br> May need to be increased for high latency environments.');
	let pollTip =printTip('Frequency of job polling requests. <br> Lower frequency can improve job update responsiveness in the UI, but can cause more traffic with multiple A3s.');
	let dbSyncTip = printTip('Similar to job polling, but effects the display of new and changed systems in the UI.  <br>Only change this if directed.');
	let maxOnTip = printTip('The default backup retention for all backups. <br>Can be overridden for each system, or by using GFS on the backup job.');
	let maxOffTip = printTip('The default vault retention for all vaulted systems. <br>Can be overridden for each system, or by using GFS on the backup job.');
	let emailTip =printTip('If enabled, this email will recieve alerts and job notifications');

	let strictTip =printTip('When enabled, only daily backups are eligable for promotion.');
	let apiKeyTip =printTip('Authentication key for client API Access.');

	var email = "";
	if('smtpToAddress' in sets){ email = sets.smtpToAddress; }
	let passTip = printTip('Set the password for the "alike" user for the web login and the container ssh (TCP 222)');
	let looseCacheTip = printTip('Improves initial backup performance using a cache from similar systems.<br> <br>Generally recommended.');

	var row = `
	<div class='row'>

	<div class='col'>

		<div class="card card-primary shadow"> <div class="card-header"><h3 class="card-title">Web Manager Settings</h3> </div>
		<div class="card-body">
		<table>
		<tr><td><b>Login Session Timeout (sec):</b> ${sessTip}
		</td><td> <input id='sessionTimeout' type='number' class='form-control mgr-setting' placeholder='<default value>' ></td></tr>
		<tr><td><b>WebSocket Timeout (sec):</b>${webSockTip}
		</td><td> <input id='wsTimeout' type='number' class='form-control mgr-setting' placeholder='<default value>' ></td></tr>
		<tr><td><b>Job polling (sec):</b> ${pollTip}
		</td><td> <input id='jobPollSec' type='number' class='form-control mgr-setting' placeholder='<default value>' ></td></tr>
		<tr><td><b>DB sync (sec):</b> ${dbSyncTip}
		</td><td> <input id='dbSyncSec' type='number' class='form-control mgr-setting' placeholder='<default value>' ></td></tr>
		<tr><td><b>System IP Address:</b>${manageIPtip}
		</td><td> <input id='managerIP' type='text' class='form-control mgr-setting' placeholder='<Our IP>'></td></tr>
		<tr><td><b>Alike User Password:</b> ${passTip}
		</td><td> <input id='uiPass' type='password' class='form-control mgr-setting'  ></td></tr>
		<tr><td><b>Client API Key:</b>${apiKeyTip}
		</td><td> <input id='apiKey' type='text' class='form-control mgr-setting' placeholder='<Key for API Access>'></td></tr>
		<tr><td><b>A3 Log Level:</b>
		</td><td> 
		<div class='form-group'> 
		<select class='form-control mgr-setting' id='debugLevel'>
			<option value=7>Debug</option>
			<option value=6>Info</option>
			<option value=4>Warning</option>
			<option value=3>Error</option>
		</select> </div>
		</td></tr>
		</table>
		</div> </div> 

		<div class="card card-info shadow"> <div class="card-header"><h3 class="card-title">Global Backup Settings</h3> </div>
		<div class="card-body">
		<table>
		<tr><td><b>Default Backups per System:</b> ${maxOnTip}
		</td><td> <input id='numVersions' type='number' class='form-control mgr-setting' placeholder='<default value>' ></td></tr>
		<tr><td><b>Default Vaults per System:</b> ${maxOffTip}
		</td><td> <input id='numVersionsOffsite' type='number' class='form-control mgr-setting' placeholder='<default value>' ></td></tr>

		<!-- <tr><td><b>Email Alert Recipient:</b> ${emailTip}
		</td><td> <input id='smtpToAddress' type='text' class='form-control mgr-setting' placeholder='<user@account.com>' ></td></tr>-->


		<tr><td><b>Allow loose cache matching:</b> ${looseCacheTip}
		</td><td> ${printToggle("allowFuzzyHCA","", sets.allowFuzzyHCA, "mgr-setting") }</td></tr>
		</table>
		</div> </div> 
	</div>

	<div class='col'>


        <div class="card card-secondary shadow"> <div class="card-header"><h3 class="card-title">Global GFS Profiles</h3> </div>
        <div class="card-body">
	<input id='gfsid' type='hidden' value=0>
        <table width=99%>
        <tr><td width=220> <b>Select Profile:</b></td><td colspan=3><div class='form-group'> <select class='form-control' id='gfsList'></select> </div></tr>

        <tr class='gfsEdit'><td><b>Profile Name:</b>
        </td><td colspan=3> <input id='gfsName' type='text' class='form-control mgr-setting' placeholder='<Name this GFS Profile>' ></td></tr>

        <tr class='gfsEdit'><td><b>Daily backups retained:</b> 
        </td><td> <input id='dailies' min=0 type='number' class='form-control mgr-setting' ></td><td colspan=2></td></tr>

        <tr class='gfsEdit'>
		<td><b>Weekly backups retained:</b> 
	        </td><td width=75px > <input id='weeklies' min=0 type='number' class='form-control mgr-setting' ></td>
		<td width=150px> &nbsp;<b>Promoted from:</b> 
	        </td><td > 
			<div class='form-group'>
			<select id='weeklyPromote' class='form-control'>
			<option value=0>Sun</option>
			<option value=1>Mon</option>
			<option value=2>Tue</option>
			<option value=3>Wed</option>
			<option value=4>Thu</option>
			<option value=5>Fri</option>
			<option value=6>Sat</option>
			</select>
			</div>
		</td>
	</tr>

        <tr class='gfsEdit'>
		<td><b>Monthly backups retained:</b> 
	        </td><td > <input id='monthlies' min=0 type='number' class='form-control mgr-setting' ></td>
		<td> &nbsp;<b>Promoted from:</b> 
	        </td><td > <div class='form-group'><select  class='form-control' id='monthlyPromote'><option value=0>First Day</option><option value=1>Last Day</option></select></div></td>
	</tr>

        <tr class='gfsEdit'>
		<td><b>Yearly backups retained:</b> 
	        </td><td > <input id='yearlies' min=0 type='number' class='form-control mgr-setting' ></td>
		<td> &nbsp;<b>Promoted on:</b> 
	        </td><td > 
			<div class='form-group'>
			<select id='yearlyPromote' class='form-control'>
				<option value=0>Jan</option>
				<option value=1>Feb</option>
				<option value=2>Mar</option>
				<option value=3>Apr</option>
				<option value=4>May</option>
				<option value=5>Jun</option>
				<option value=6>Jul</option>
				<option value=7>Aug</option>
				<option value=8>Sep</option>
				<option value=9>Oct</option>
				<option value=10>Nov</option>
				<option value=11>Dec</option>
			</select>
			</div>
		</td>
	</tr>
        <tr class='gfsEdit'>
		<td colspan=4>
                      <div class="form-group"> 
			<input type='checkbox' id='gfsStrict'>
                          <label for="gfsStrict" >(Strict) Only promote Monthly from a Daily backup ${strictTip}</label>
                        </div>
		</td>
	</tr>
	<tr><td colspan=4 align='right'>
		<button class='btn btn-warning btn-sm' id='gfsDel' disabled><i class='fas fa-trash'></i> Delete Profile</button> 
		<button class='btn btn-info btn-sm' id='gfsSave' disabled>Save Profile</button> 
	</td></tr>

        </table>
        </div> </div> </div>

	</div> <!-- end row -->
	<div class='row justify-content-end'><button class='btn btn-success save-settings'>Save</button> </div>`;
	
        $("#dataTable").append(row);
	renderTips();

	$("#sessionTimeout").val(sets.sessionTimeout);
	$("#wsTimeout").val(sets.wsTimeout);
	$("#jobPollSec").val(sets.jobPollSec);
	$("#dbSyncSec").val(sets.dbSyncSec);
	$("#managerIP").val(managerIP);
	$("#apiKey").val(sets.apiKey);
	$("#numVersions").val(sets.numVersions);
	$("#uiPass").val(sets.uiPass);
	$("#numVersionsOffsite").val(sets.numVersionsOffsite);
	$("#smtpToAddress").val(email);
	$("#allowFuzzyHCA").val(sets.allowFuzzyHCA);
	if(sets.debugLevel){ $("#debugLevel").val(sets.debugLevel); }

	$(".gfsEdit").find("input,button,select").attr("disabled", "disabled");

	if(!data.mode || data.mode ==2){
		$("#managerIP").prop("disabled", true);
	}
        wsCall("gfsProfiles",null, "gfsProfileList", null);
}

function gfsProfileList(data){

	$("#gfsList").empty();

	if(data.profiles.length==0){
		$("#gfsList").append(`<option value=-2>No GFS Profiles Defined!</option>`);
	}else{
		$("#gfsList").append(`<option value=-2>Select a profile</option>`);
		$.each(data.profiles, function(i, p){
			$("#gfsList").append(`<option value=${p.gfsId}>${p.name}</option>`);
		});
	}
	$("#gfsList").append(`<option > _________ </option>`);
	$("#gfsList").append(`<option value=-1> ---Add New Profile---</option>`);
}

function populateGfsEdit(data){
	if(data.result == "error"){
		return doToast(data.message, "GFS Profile Error", "warning");
	}
	$("#gfsid").val(data.profile.gfsId);
	$("#gfsName").val(data.profile.name);
	$("#gfsStrict").prop('checked', false);
	$("#dailies").val(0);
	$("#weeklies").val(0);
	$("#monthlies").val(0);
	$("#yearlies").val(0);
	$.each(data.profile.instances, function(i, p){
		if(p.policy ==1){
			$("#dailies").val(p.versions);
		}else if(p.policy ==2){
			$("#weeklies").val(p.versions);
			$("#weeklyPromote").val(p.card);
		}else if(p.policy ==4){
			$("#monthlies").val(p.versions);
			$("#gfsStrict").prop('checked', true);
			if(p.card ==0){ 
				$("#monthlyPromote option[value='0']").attr('selected', 'selected');
			}else{
				$("#monthlyPromote option[value='1']").attr('selected', 'selected');
			}
			
		}else if(p.policy ==5){
			$("#yearlies").val(p.versions);
			$("#yearlyPromote").val(p.card);
		}else if(p.policy ==3){
			$("#monthlies").val(p.versions);
			$("#monthlyPromote").val(p.card);
		}
	
	});
	
}

async function doSaveSettings(){
	showLineLoad();
	var settings ={};
	var args = {};	// this can never be an array (duh)
	$.each($(".mgr-setting"), function(i,s){
		settings[ $(this).attr("id") ]  =$(this).val();
	});
	try{
		args["settings"] = settings;
		var res = await wsPromise("setting/saveAll", args, 0);	// settings gets them all, setting sets one or more
		// do something w/ the alerts
		doToast("Settings saves successfully", "Saved Settings", "success");
	} catch (ex){
		doToast(ex.message, "Failed to save settings", "warning");
	}
	finishLineLoad();

}

///////////////////
function pollForRibbon(){
	getRibbon();
	window.statusTimerID = setInterval(function() { getRibbon(); }, 6 * 1000);
}
async function getRibbon(){
        try{
                var res = await wsPromise("ribbon", null, 0);
		showAlerts(res.alerts);
		if($("#dash-alerts").length){
			drawDashAlerts(res);
		}
		checkRunningJobs(res.jobs);
        } catch (ex){
                doToast(ex.message, "Failed to gather A3 notices", "warning");
        }
}

function checkRunningJobs(jobs){

	let cache = sessionStorage.getItem("curJobs") ;
	if(cache == null){
		//first time
		let obj = JSON.stringify(jobs);
		sessionStorage.setItem("curJobs", obj );
		console.log("Added the job cache for the first time");
		return;
	}
	cache = JSON.parse(cache);

	if(jobs.length != cache.length ){
		console.log(jobs.length +" jobs are active");
		var done = jobDiff(cache, jobs);
		if(done.length > 0){
			let clearCache = false;
			//console.log("Job(s) completed:", done);
			jQuery.each(done, function(i,j){
				let ttype = "success";
				if(j.status == JobStatus.warning || j.status == JobStatus.failed){ ttype = "warning"; }
				let jt = "Backup";
				//let st = getStatusDesc(j.status);
				let rt = timeElapsed(j.timeElapsed);
				let msg = `${jt} job: ${j.name} finished <br>
					Run time:  ${rt}
					`;
				doToast(msg, "Job completed", ttype);
				console.log(msg);

				if(j.type == 10){ clearCache=true; }	// our meta cache needs updating
			
			});

			// check if we're on the job history page
			if ($("#jobHistory").length > 0) {
				getJobHistory();
			}
			if(clearCache){
				console.log("Rebuilding meta-caches... this could take a while");
				clearMetaCaches();
				buildMetaCaches();
			}
		}
	}

	let obj = JSON.stringify(jobs);
	sessionStorage.setItem("curJobs", obj );

}
// Function to find objects in prev that are not present in cur
function jobDiff(prev, cur) {
    return prev.filter(function(obj1) {
        return !cur.some(function(obj2) {
            return obj1.jobID === obj2.jobID;
        });
    });
}

function showAlerts(all){
	$("#alert-messages").html('');
	var row = "";
	var max = 10;
        jQuery.each(all, function(i,r){
		var ts = timeSince(r.timestamp);

		row += `<a href="#" class="dropdown-item"><div class="media"> <div class="media-body"> <h3 class="dropdown-item-title"> A3 ${r.source}
		<span class="float-right text-sm text-danger"><i class="fas fa-times-circle delete-alert" data-id='${r.id}' '></i></span>
		</h3> <p class="text-sm">'${r.message}'</p> <p class="text-sm text-muted"><i class="far fa-clock mr-1"></i> ${ts}</p> </div></div></a>`;


		max--;
		if(max ==0){ 
			var left = all.length - 10;
			row += '<a href="#" class="dropdown-item"> <i class="fas fa-exclamation mr-2"></i> '+left;
			row += ' additional messages <span class="float-right text-muted text-sm">'+left+' more</span> </a> <div class="dropdown-divider"></div>';
			return false; 
		}
		
	});
	var num = all.length;
	$("#alert-messages").html(row);
	if(num ==0){
		$("#num-alerts-badge").html('');
	}else{
		$("#num-alerts-badge").html(num);
	}
	$("#num-alerts-header").html(num);

}
///////////////////
//

function drawBackupExplorer(data, site){
	if(data.result == "error"){
		doToast(data.message, "Error getting information", "error");
	}

	finishLineLoad();
	let relpath = data.relpath;
	if (relpath == "//"){ relpath = '/'; }
	let ds = data.ds;
        var sets = data.settings;
        setupPage(" <a href='#' class='go_backup-explorer'>Backup Explorer</a>", "Backup Explorer");
        $("#dataTableDiv").append("<div id='dataTable' class='p-3'></div>");

	let a3s = JSON.parse(sessionStorage.getItem("a3Cache"));
        var row = "<div class='container-fluid'>";


	//begin header
	let chked = "";
	if(ds ==1){ chked ="checked"; }
        row += `<div class='row'>
			<div class='col-1'>
			<select class='form-control' id='flr-site' >`;
	$.each(a3s, function(i, a3){
		row += `<option value=${a3.id}>${a3.name}</button>`;
	}); 
	row +=`</select>
		</div>
		<div class='col-1'>

		<div class="toggle-button" id="ds-toggle-button">
			<input type="checkbox" ${chked} class="flr-ds-checkbox" id='flr-ds' data-site=${site} data-ds=${ds}>
			<div class="knobs"> <span></span> </div>
			<div class="layer"></div>
		</div>
		</div>
		<div class='col-10'>

		Path: &nbsp; <span class='clickable flr_folder' data-path='/' data-site=${site} data-ds='${ds}' data-name='/'><b>/</b></span> `;

        var parts = relpath.split('/');
        var builder = "";
        $.each(parts, function(i, val){
                if(val == ""){ return true; }
		if (i > 0){
//			if (val.startsWith('/')) { val = val.substring(1); }
//		}else if (i > 1){ 
//			val = "/"+val 
		}
                var cval =  val.replace(/^\/+|\/+$/g, '');
                var pre = builder;
                builder += val;
//		if(relpath == '/' || relpath ==""){ val = val.replace(/^[\d]+_/, ''); }
		if (i ==1){ val = val.replace(/^[\d]+_/, ''); }
		val += "/";
                var tmp = `<span class='clickable flr_folder' data-path='${pre}/' data-site=${site} data-ds='${ds}' data-name='${cval}/'>${val}</span>`;
                row += tmp;
        });
	row += "</div> </div>";	// end header
	row += "<div class='row'>";
	row += `<table id='flrList' width=100%><thead><tr><th>File Name</th><th>Size</th><th>Modified Date</th></tr></thead><tbody> `;
        if (relpath != "/" && relpath != "" && relpath != ".."){
                row += "<tr>";
                var tmp = `<span class='clickable flr_folder' data-path='${relpath}' data-ds='${ds}' data-site=${site} data-name='..'> 
                	<i class='fas fa-level-up-alt fa-fw fa-lg'></i>... </span>`;
                row += "<td>"+ tmp +"</td><td></td><td></td>";
        }

        jQuery.each(data.data, function(i,d){
                row += "<tr>";
                var icon = "<i class='fas fa-folder fa-fw fa-lg' style='color:#f5c842;' title='Directory'></i>";
                var lepoch = `<i class='hide-text'>${d.lastmod}</i>`;
                var lsz = `<i class='hide-text'>${d.size}</i>`;
		let lmod = getDate(d.lastmod);
                if(d.type == "dir"){
                        var cname = d.name.replace(/^\/|\/$/g, '');
			var dname = cname;
			if(relpath == '/' || relpath ==""){ dname = cname.replace(/^[\d]+_/, ''); }
                        row += `<td class='pad-10'>
					<span class='clickable flr_folder' data-site=${site} data-path='${relpath}' data-ds='${ds}' data-name='${cname}/'>${icon} ${dname}</span>
					</td>
					<td><i class='hide-text'></i> - </td>
					<td>${lepoch} ${lmod}</td>`;
                }else{
                        if (!relpath.endsWith('/')) { relpath += '/'; }
                        var cname = d.name.replace(/^\/|\/$/g, '');
			let psize = prettySize(d.size);
                        icon = "<i class='"+d.icon+" fa-fw fa-lg' ></i>";
                        // test direct href download
                        //var dl = '&nbsp; <a href="/ws/flrDownload?session='+window.currentSessionID +'&file='+relpath+d.name+'&ds='+ds+'" target=_new title="Download '+cname+'"> </a>';
						//<i class='fa fa-arrow-alt-circle-down fa-lg' style='color: #4e9a06;'></i> 
                        row +=  `<td class='pad-10'>
					<span class='flr_file' data-site=${site} data-path='${relpath}' data-ds='${ds}' data-name='${cname}'>${icon} ${cname} &nbsp; 
					</span>
					</td>
				<td>${lsz} ${psize}</td>
				<td>${lmod}</td>`;
                }
                row += "</tr>";
        });

	row += `</tbody></table>
		</div>
		</div>
		`;

	$("#dataTable").append(row);
}

function browseFlr(site, ds, relpath){
	showLineLoad();
        var args = new Object();
        args["path"] = relpath;
        args["ds"] = ds;
	wsCall("browseRestoreFS",args,"drawBackupExplorer",site, site );
}
function showVmInFlr(site, ds, uuid, ts){
	showLineLoad();
        var args = new Object();
        args["uuid"] = uuid;
        args["ds"] = ds;
        args["ts"] = ts;
	wsCall("browseRestoreFS",args,"drawBackupExplorer",site, site );
}


///////////////////
//

function getActiveJobs(){
        var args = new Object();
	args["active"] ="1";
	wsCall("jobs", args, "drawJobsTable", null);
}
function getJobHistory(offset=0){
        var args= new Object();
        args["offset"] = offset;
        wsCall("jobs", args, "drawJobsTable",null );
}


// List all defined A3s 
function listA3s(){
	clearTimer();
        var args = new Object();
	wsCall("a3s", args, "drawA3sTable", null);
}
// List all defined ABDs 
function listABDs(){
	clearTimer();
        var args = new Object();
	wsCall("abds", args, "drawA3sTable", null);
}
function runSchedule(id, site){
        var args= new Object();
        args["id"] = id;
	wsCall("schedule/run", args, "runScheduleCb", null,site);
}
function runScheduleCb(){
	getActiveJobs();
}


function listInstaboots(){
	showLineLoad();
        wsCall("a3s", null,"drawInstaboots",null);
}



