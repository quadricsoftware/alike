
// Event handlers
$( document ).ready(function() {
	if($("#a3_site_manager").length > 0){
		window.isManager=1;
	}

    window.isConnected=true;
    pollForRibbon();
//    drawDash();	// moved to entrypoint


	$("#loadingLine").css("display", "none");

	$("#loadingLine").on("animationend", function () {
		$("#loadingLine").css("display", "none");
	});
    
    // for the backup selection page
    $(function() {
        $( ".selectableItems" ).sortable({
          connectWith: ".selectableItems"
        }).disableSelection();
      });
      
      doAccordian();
      doPicker();
	$(window).scroll(function() { windowScroll(); });
	$("#detailModal").scroll(function() { modalScroll(); });
	
	renderTips();
});

$(document).on('click', '.kb_metarefresh', function(){ window.open("https://docs.alikebackup.com/kb-v3/faqs/understanding-alike-metarefresh/", "_blank"); });
$(document).on('click', '.qs_preview_link', function(){ window.open("https://docs.alikebackup.com/kb-v3/guides/a3-75-quick-start-guide/", "_blank"); });
$(document).on('click', '.qs_portal_link', function(){ window.open("https://portal.alikebackup.com", "_blank"); });
$(document).on('click', '.qs_kb_link', function(){ window.open("https://docs.alikebackup.com/kb-v3/", "_blank"); });
$(document).on('click', '.qs_admin_guide_link', function(){ window.open("https://docs.alikebackup.com/admin/7/", "_blank"); });
$(document).on('click', '.qs_support_link', function(){ window.open("https://portal.alikebackup.com/?page=support", "_blank"); });

function modalScroll(){
	if($("#detailModal").is(":visible")){
		if($("#detailModal").scrollTop() > 50 ){
			$(".goto-top").show();
		}else{
			$(".goto-top").hide();
		}
	}
}
function windowScroll(){
	if($(window).scrollTop() > 50 ){
		$(".goto-top").show();
	}else{
		$(".goto-top").hide();
	}
}

function dropAction(){    
    var ting = $( "#backupTargets" ).find( "div.hook" );
    ting.addClass("trashable");
    ting.html("x");
    
    ting = $( "#restoreVersions" ).find(".vmVersion");
    ting.addClass("trashable_v");
    ting.html("x");
    
    ting = $( "#replicatedSystems" ).find("div.hook");
    ting.addClass("trashable");
    ting.html("x");
}

function doPicker(){
    $(".selectableItems" ).sortable({
          connectWith: ".selectableItems",
          update: function(event, ui) {
            dropAction();
            }
        }).disableSelection();      
}
function doAccordian(){
    $("#hostPicker" ).accordion({
      collapsible: true,
      active: false,
      heightStyle: "fill"
    });   

}
function doAccordianRest(){
    $("#backupPicker" ).accordion({
      collapsible: true,
      active: false,
      heightStyle: "fill"
    });
}
function doAccordianCust(elem){
    $("#"+elem ).accordion({
      collapsible: true,
      active: false,
      heightStyle: "fill"
    });
}
function doAccordianCust2(clazz) {
	$("." + clazz).accordion({
	      collapsible: true,
	      active: -1,
	      heightStyle: "fill"
	    });
}



$(document).on('click', '.qsPortal', function(){
	window.open( getText("GsgUrl", '_blank') );
});

$(document).on('click', '.refresh-sub-info', function(){
	wsCall("subsync",null,null,null );
	doToast("Refreshing subscription information from the Web.  This may take a few moments.", "Refresh Started", "success");
	setTimeout(function() { 
		wsCall("settings", null, "drawSubscription", null);
	}, 1000);	
});

function refreshSubInfo(){
	wsCall("subupdate",null,"updateSubInfo",null );
}

$(document).on('click', '.goto-top', function(){
	$(window).scrollTop(0);
	$('#detailModal').animate({ scrollTop: 0 });
});

$(document).on('change', '#show_only_vaulted', function(){
	if($('#show_only_vaulted').is(':checked')){
		sessionStorage.setItem("show_only_vaulted",1 );
		listVMs(2);
	}else{
		sessionStorage.setItem("show_only_vaulted",0 );
		listVMs(1);
	}
});

$(document).on('change', '#syslog_filter', function(){
	var f = $("#syslog_filter").val();
	sessionStorage.setItem("cur_log_filter",f );
	$("*[class^='loglevel_'").each(function() { // Grab all elements with a title attribute,and set "this"
		var classname = $(this).attr("class");
		var lev = classname.substr(classname.length -1);
		if(lev <= f){ 
			$(this).show();
		}else{
			$(this).hide();
		}
	});
	
});

$(document).on('click', '.schedule-delete-menu', function(){
	var $parentUl = $(this);
	var id = $parentUl.data("id");
	var site = $parentUl.data("site");
	deleteSchedule(id, site);
});
$(document).on('click', '.schedule-edit', function(){
	var args= new Object();
	var $guy = $(this);
	args["id"] = $guy.data("id");
	var site = $guy.data("site");
	var type = $guy.data("type");
	let cb = "editBackupSchedule";	
	if(type == 1){ cb = "editRestSchedule"; }
	if(type == 5){ cb = "editRepSchedule"; }
	showLineLoad();
	wsCall("schedule", args, cb, site, site);
});
$(document).on('click', '.schedule-run-menu', function(){
	var $parentUl = $(this);
	var id = $parentUl.data("id");
	var site = $parentUl.data("site");
	runSchedule(id, site);
});

$(document).on('click', '.schedule-run', function(){
	var id = $(this).data("id");
	var site = $(this).data("site");
	runSchedule(id, site);
});
$(document).on('click', '.schedule-delete', function(){
	var id = $(this).data("id");
	var site = $(this).data("site");
	deleteSchedule(id, site);
});

$(document).on('click', '.host-edit', function(){
	var args= new Object();
	let guid = $(this).data("guid");
	args["guid"] = guid;
	args["ip"] = $(this).data("ip");
	args["user"] = $(this).data("user");
	let raw = $("#host-a3s-"+guid).val();
	let a3ids =[];
	$.each(raw, function(i, r){
		let a = getA3FromGuid(r);
		a3ids.push(a.id);
	});
	args["a3s"] = a3ids;
	drawEditHostModal(args, 2);
});
$(document).on('click', '.host-details', function(){
	var id = $(this).data("id");
	viewHostDetails(id);
});
$(document).on('click', '.host-refresh', function(evt) { refreshHost($(evt.currentTarget)); });
async function refreshHost(guy){

        var args= new Object();
        args["guid"] =  guy.data("guid");

        try{
                var res = await wsPromise("host/refresh", args, 0);
                doToast("Meta-refresh job has started on host.  Please check active jobs for more details", "Refresh job started", "success");
        } catch (ex){
                doToast(ex.message, "Failed to run Meta Refresh on host", "warning");
        }


}
$(document).on('click', '.host-add-xen', function(){
	drawEditHostModal(null, 2);
});
$(document).on('click', '.host-add-hv', function(){
	drawEditHostModal(null, 3);
});
$(document).on('click', '#add-host-save', function(evt) { setHost(evt); });
async function setHost(evt){
	evt.preventDefault();
	doAddHost();
}
$(document).on('click', '#testHost', function(evt) { testHost(evt); });
async function testHost(evt){
	evt.preventDefault();
	doTestHost();
}

$(document).on('click', '.a3-edit', function(){
	var id = $(this).data("id");
	var ip = $(this).data("ip");
	var pass = $(this).data("pass");
	var o ={};
	o.a3id = id;
	o.ip = ip;
	o.password = pass;
	drawEditA3Modal(o);
});
$(document).on('click', '.a3-add', function(){
	drawEditA3Modal(null);
});

$(document).on('click', '.launch-wizard', function(){
	doLaunchWizard();
});
$(document).on('click', '.add-rep-sched', function(){
	addReplicateSchedule();
});
$(document).on('click', '.add-rest-sched', function(){
	addRestoreSchedule();
});
$(document).on('click', '.add-back-sched', function(){
	addBackupSchedule();
});
$(document).on('click', '.newGFS', function(){
	newGFSProfile();
});
$(document).on('click', '.view-vm-details', function(){
	var id = $(this).data("id");
	var uuid = $(this).data("uuid");
	var site = $(this).data("site");
	if(!isNumeric(site)){ site=0; }

	var args= new Object();
	args["backups"] = 1;
	wsCall("backups", args, "drawVmDetails", uuid);
});
$(document).on('click', '.list-job-details', function(){
	var id = $(this).data("id");
	var site = $(this).data("site");
	if(!isNumeric(site)){ site=0; }
	listJobDetails(id, site);
});
$(document).on('click', '.quick-backup-nolic', function(){
	doToast("System is not licensed!<br>Please license this system to perform backups.","System not licensed", "error");
});
$(document).on('click', '.quick-backup', function(){
	var id = $(this).data("uuid");
	var a3id = $(this).data("a3id");
	runQuickBackup(id, a3id);
});
$(document).on('click', '.delete-vm', function(){
	var id = $(this).data("id");
	deleteVM(id);
});
$(document).on('click', '.validate-version', function(){
	var args= new Object();
	args["uuid"] = $(this).data("uuid");
	args["ts"] = $(this).data("ts");
	args["ds"] = $(this).data("ds");
	var site = $(this).data("site");
	wsCall("version/validate",args,null,null,site);
	doToast("Validation job has been started","Job Started", "success");
});
$(document).on('click', '.vault-version', function(){
	var id = $(this).data("id");
	var vers = $(this).data("version");
//	vaultVersion(id, vers );
});
$(document).on('click', '.full-restore-version', function(){

	var id = $(this).data("id");
	var vers = $(this).data("version");
	restoreAsVM(id, vers );
});
$(document).on('click', '.retain-version', function(){
	var id = $(this).data("id");
	var vers = $(this).data("version");
	var site = $(this).data("site");
	retainVMVersion(id, vers, site);
});
$(document).on('click', '.delete-version', function(){
	var id = $(this).data("id");
	var vers = $(this).data("version");
	var site = $(this).data("site");
	deleteVMVersion(id, vers, site);
});


$(document).on('click', '.delete-alert', function(){
	var id = $(this).data("id");
	var args= new Object();
	args["id"] = id;
	wsCall("alerts/dismiss", args, null, null, 0);
	$(this).closest('tr').remove();
	if ($('#dash-alerts').find('tr').length === 0) {
		$('#dash-alerts').append(`<tr><td colspan=2 align='middle'>No alerts to display</td></tr>`);
	}
});

$(document).on('click', '#clear-all-alerts', function(){
	wsCall("alerts/dismissAll", null, null, null, 0);
});

$(document).on('click', '.clear-alert', function(){
	event.preventDefault();
	var id = $(this).data("id");
	var site = $(this).data("site");
	if(id > 0){
		$(this).closest(".dropdown-item").remove();
		$("#num-alerts-badge").text((_, currentText) => Number(currentText) - 1);	// knock the badge down a notch
	}else{
//		var ul = $("#alert-list");
//		ul.find("li").each(function() { $(this).remove(); });
	}
	var args= new Object();
	args["id"] = id;
	wsCall("alerts/dismiss", args, null, null, site);
});


$(document).on('change', '#backupDays', function(){
    var val=$(this).val();
    listBackups(val);
});
$(document).on('change', '#vaultDays', function(){
    var val=$(this).val();
    listVaults(val);
});

$(document).on('change', '#selectLog', function(){
    var val=$(this).val();
    viewLogs(val);
});
$(document).on('change', '#langChoice', function(){
    var val=$(this).val();
    setLanguage(val);
    loadTextStrings(val);
});
$(document).on('change', 'input[name="timeChoice"]:radio', function(){
    var val = $("input[name=timeChoice]:checked").val();
    //var val=$(this).val();
    setTimeFmt(val);
    
});
$(document).on('change', '#graphAnimate', function(){
    var val=0;
    if($('#graphAnimate').is(':checked')){
        val=1;
    }
    
    //var doit = 0;
    //if(isCheckedBool(val) || val =="on" ){ doit =1; }
    setGraphAnimate(val);    
});
$(document).on('click', '.check-updates', function(){
	checkForUpdate(true);
});


$(document).on('click', '.closeNote', function(){
    var fade = { opacity: 0, transition: 'opacity 0.5s' };
    $(this).parent().css(fade);
    $(this).parent().slideUp(); // could use .remove(), .slideUp() etc
});
    
// highlight a full row in main data_table when clicked
 $(document).on('click', '#dataTable tr', function(){  
    //var state = $(this).hasClass('highlighted');
    $('.highlighted').removeClass('highlighted');
    //if ( ! state) { $(this).addClass('highlighted'); }
    $(this).addClass('highlighted');
});
// highlight a full row in main data_table when clicked
 $(document).on('click', '#sub-page-header li', function(){  
    //var state = $(this).hasClass('highlighted');
    $('.settings-highlighted').removeClass('settings-highlighted');
    //if ( ! state) { $(this).addClass('highlighted'); }
    $(this).addClass('settings-highlighted');
});

$(document).on('click', '#job_search_submit', function(event){
    event.preventDefault();
    var q = $("#search_text").val();
    if(q){
        getJobHistory(q,"drawJobsTable",0);        
    }
});

// settings changers
$(document).on('change', '.settingOptionOld', function(){
    if($(this).attr('type')== "checkbox"){
        var val="false";
        if($(this).is(':checked')){
            val ="true";
        }
        if($(this).attr('name')== "blockCompression"){
            val = "0";
            if($(this).is(':checked')){
                val ="1";
            }
        }
        updateSetting($(this).attr('name'), val);
    }else if($(this).attr('type')== "text" || $(this).attr('type')== "password"){
        var val = $(this).val();
        if($(this).attr('name')== "BDBFileMaxSize"){
            val = val *1024;
        }        
        updateSetting($(this).attr('name'), val);
    }else if($(this).prop('type')== "select-one"){
        var val=$(this).val();
        updateSetting($(this).prop('name'), val);
    }
});
$(document).on('click', '#save-all-settings', function(){
    var args= new Object();

    $('.settingOption').each(function() { // Grab all elements with a title attribute,and set "this"
	var name = $(this).attr('name');


	if($(this).attr('type')== "checkbox"){
		var val="false";
		if($(this).is(':checked')){ val ="true"; }
		if($(this).attr('name')== "blockCompression"){
		    val = "0";
		    if($(this).is(':checked')){ val ="1"; }
		}
	}else if($(this).attr('type')== "text" || $(this).attr('type')== "password"){
		var val = $(this).val();
	}else if($(this).prop('type')== "select-one"){
		var val=$(this).val();
	}
	if(val == null || val == "undefined"){ 
		val = ""; 
	};

	args[name] = val;
    });
	saveAllSettings(args);
});

$(document).on('click', '#csv-export', function(){
	exportCSV(window.report_csv, "Alike_backup_report.csv");	
});

$(document).on('click', '.save-settings', function(evt) { saveSettingsA3(evt); });
async function saveSettingsA3(evt){
	doSaveSettings();
}
$(document).on('click', '#testA3', function(evt) { testA3(evt); });
async function testA3(evt){
	evt.preventDefault();
	doTestA3();
}

$(document).on('click', '#add-a3-save', function(evt) { setA3(evt); });
async function setA3(evt){
	evt.preventDefault();
	doAddA3();
}
$(document).on('click', '.a3-delete', function(){
	var id = $(this).data("id");
	doDeleteA3(id);
});


$(document).on('click', '#smtpNotify', function(){
    if($(this).is(':checked')){        
        $('.emailOptions').removeAttr('disabled');
        $('#emailContainer').css('background','');
    }else{
        $('.emailOptions').attr('disabled', 'disabled');
        $('#emailContainer').css('background','#EDEAE8');
    }
});
$(document).on('click', '#enableOnsiteVaulter', function(){
    if($(this).is(':checked')){        
        $('.odsOptions').removeAttr('disabled');
        $('#odsContainer').css('background','');
    }else{
        $('.odsOptions').attr('disabled', 'disabled');
        $('#odsContainer').css('background','#EDEAE8');
    }
});

$(document).on('click', '#backup', function(){
    if($(this).is(':checked')){        
        $('#vaultEnable').removeAttr('disabled');
        $('#gfsEnable').removeAttr('disabled');        
    }else{
        $('#vaultEnable').attr('disabled', 'disabled');
        $('#gfsEnable').attr('disabled', 'disabled');        
    }
});


$(document).on('click', '#showInitialWelcome', function(){
    if($(this).is(':checked')){
        updateSetting("showInitialWelcome", "true");        
    }else{
        updateSetting("showInitialWelcome", "false");        
    }
});

$(document).on('click', '#enableOnsiteVaulter', function(){
    $('#ods-message').text("");

    if($(this).is(':checked')){
        updateSetting("enableOnsiteVaulter", "true");
        setVaultingState(true);
        
        getVaultingState();
        setTimeout(function(){ getVaultingState(); }, 1000);
        
    }else{
        updateSetting("enableOnsiteVaulter", "false");
        setVaultingState(false);
    }
});


$(document).on('click', '#smtpTest', function(){
    if($('#smtpNotify').is(':checked')){
        $('#modal-error').text("");
        testEmail();
    }
});




$(document).on('click', '#abdIPSave', function(){
    $("#abdip-message").removeClass("modal-error");
    $("#abdip-message").removeClass("modal-success");
$("#abdip-message").html();
    if($('#useDHCP').is(':checked')){
        return;
    }
       
    var id = $("#netid").val();
    var args= new Object();
    args["id"] = id;
    args["ip"] = $("#abdIP").val();
    args["netmask"] = $("#netmask").val();
    args["gw"] = $("#gateway").val();
    args["MAC"] = $("#MAC").val();
    args["xennet"] = $("#xennets").val();
    args["poolid"] = $("#poolid").val();
    if(args["ip"].length < 8){
        $("#abdip-message").addClass("modal-error");
        $("#abdip-message").html(getText('invalid-ip'));
    }else if(args["netmask"].length < 9){
        $("#abdip-message").addClass("modal-error");
        $("#abdip-message").html("Invalid netmask!");
    }else if(args["gw"].length < 8){
        $("#abdip-message").addClass("modal-error");
        $("#abdip-message").html("Invalid gateway!");
    }else{
	if(args["MAC"].length < 12){ args["MAC"] = generateMAC(); }
	wsCall("abdnet/set",args,"editABDIPCallback",null );
    }
});

$(document).on('change', '#abdIP', function(){
	if(!$("#netmask").val()){
		$("#netmask").val("255.255.255.0");
	}
	if(!$("#MAC").val()){
		$("#MAC").val(generateMAC());
	}
});
$(document).on('change', '#auto-accessip', function(){
    if($(this).is(':checked')){
        $("#accessip").val('');        
        $('#accessip').attr('disabled', 'disabled');
        var site = $("#vm-details-site").val();
        var args= new Object();
        args["value"] = "";
        args["type"] = "accessip";
        args["vmuuid"] = $("#vmuuid").val();
        //wsCallGzSite("vm/set",args,"VM access IP will be auto-detected","Failed to update VM's Access IP.", null,null,false,site);
	wsCall("vm/set",args,null,null, site );
    }else{
        $('#accessip').removeAttr('disabled');
    }
});
$(document).on('click', '.test-agent', function(){

	$("#agent-test-results").removeClass();
	$("#agent-test-results").html("Testing agent, please wait...");
	
	var site = $(this).data("site");
	var uuid = $(this).data("uuid");
        var args= new Object();
        args["uuid"] = uuid;
//        wsCallGzSite("testAgentVM",args,"Agent access test complete","Failed to acquire VM's Agent Information.", "displayVmAgentStatus",null, true,site);
	wsCall("testAgentVM", args, "displayAgentResults", 0, site);
});


$(document).on('change', '#accessip', function(){
    var val=$(this).val();
    if(!ipCheck(val)){
        $(this).val('');
        return;
    }
	var site=0;
        site = $("#vm-details-site").val();
    var args= new Object();
    args["value"] = val;
    args["type"] = "accessip";
    args["vmuuid"] = $("#vmuuid").val();
//    wsCallGzSite("vm/set",args,"Access IP updated","Failed to set VM's Access IP.", null,null,false,site);
	wsCall("vm/set",args,null,null, site );
});


$(document).on('click', '#vmdToggler', function(e){    
    e.preventDefault();
    $(this).find("span").toggle();
    $(".togglee-vmd").slideToggle();    
});

$(document).on('change', '#vm-onsite-default', function(){
    if($(this).is(':checked')){
        $("#onsiteNumVersions").val('');        
        $('#onsiteNumVersions').attr('disabled', 'disabled');
	var site=0;
        site = $("#vm-details-site").val();
        var args= new Object();
        args["value"] = "0";
        args["type"] = "onsiteversions";
        args["vmuuid"] = $("#vmuuid").val();    
	wsCall("vm/set",args,null,null, site );
    }else{
        $('#onsiteNumVersions').removeAttr('disabled');
    }
});
$(document).on('change', '#onsiteNumVersions', function(){
    var val=$(this).val();
    if(!isNumeric(val) || val <=0 ){
        $(this).val('');
        return;
    }
	var site=0;
        site = $("#vm-details-site").val();
    var args= new Object();
    args["value"] = val;
    args["type"] = "onsiteversions";
    args["vmuuid"] = $("#vmuuid").val();    
	wsCall("vm/set",args,null,null, site );
});
$(document).on('change', '#vm-offsite-default', function(){
    if($(this).is(':checked')){
        $("#offsiteNumVersions").val('');        
        $('#offsiteNumVersions').attr('disabled', 'disabled');
	var site=0;
        site = $("#vm-details-site").val();
        var args= new Object();
        args["value"] = "0";
        args["type"] = "offsiteversions";
        args["vmuuid"] = $("#vmuuid").val();    
	wsCall("vm/set",args,null,null, site );
    }else{
        $('#offsiteNumVersions').removeAttr('disabled');
    }
});
$(document).on('change', '#offsiteNumVersions', function(){
    var val=$(this).val();
    if(!isNumeric(val) || val <=0 ){
        $(this).val('');
        return;
    }
        var site = $("#vm-details-site").val();
    var args= new Object();
    args["value"] = val;
    args["type"] = "offsiteversions";
    args["vmuuid"] = $("#vmuuid").val();    
	wsCall("vm/set",args,null,null, site );
});

$(document).on('change', '#interval', function(){    
    updateOccurances();
});


$(document).on('change', '#scheduleType', function(){    
    updateSchedType();    
});

$(document).on('change', '#doCBT', function(){
    if($(this).is(':checked')){
        if($("#jobCategory").length > 0 && $("#jobCategory").val() ==1){
            $('#useEnhanced').prop("checked",false);
            $("#useQHB").prop("checked",false);
            $("#backup").prop("checked",false);            
            $("#snapshotTarget").prop("checked",false);
            $("#repCache").prop("checked",false);
            
            $('#backup').attr('disabled', 'disabled');
            $('#snapshotTarget').attr('disabled', 'disabled');
            $('#repCache').attr('disabled', 'disabled');
            
            $('#quiesced').removeAttr('disabled');
        }else if($("#jobCategory").length > 0 && $("#jobCategory").val() ==0){
            if($("#useQHB").is(":checked") ){
                $(this).prop("checked",false);
            }
        }
        
        $("#backup").prop("checked",false);
        $('#diskandmemory').prop("checked",false);
        
    }else{        
        if($("#jobCategory").length > 0 && $("#jobCategory").val() ==1){
            $('#backup').removeAttr('disabled');
            $('#snapshotTarget').removeAttr('disabled');
            $('#repCache').removeAttr('disabled');
        }
    }
});

$(document).on('change', '#useQHB', function(){
    if($(this).is(':checked')){
        $('#useEnhanced').prop("checked",false);
        adjustQHBOptions(true);
    }else{
        if($('#useEnhanced').is(':checked') == false){
            $('#useEnhanced').prop("checked",true);
            adjustQHBOptions(false);
        }
    }
});
$(document).on('change', '#useEnhanced', function(){    
    if($(this).is(':checked')){
        $('#useQHB').prop("checked",false);
        adjustQHBOptions(false);
        if($("#jobCategory").length > 0 && $("#jobCategory").val() ==1  ){
            $('#doCBT').prop("checked",false);
        }
    }else{
        if($('#useQHB').is(':checked') == false){
            $('#useQHB').prop("checked",true);
            adjustQHBOptions(true);
        }
    }
});
$(document).on('change', '#qhbfallback', function(){    
    if($(this).is(':checked')){
        $('#diskandmemory').prop("checked",false);
    }
});

$(document).on('change', '#quiesced', function(){    
    if($(this).is(':checked')){        
        $('#diskandmemory').prop("checked",false);
        $('#poweroff').prop("checked",false);
        var was = $("#doCBT").is(":checked");
        adjustQHBOptions(false);
        if(was){ $("#doCBT").prop("checked", true); }
    }
});

$(document).on('change', '#diskandmemory', function(){    
    if($(this).is(':checked')){
        $('#qhbfallback').prop("checked",false);
        $('#poweroff').prop("checked",false);
        $('#quiesced').prop("checked",false);
        $('#doCBT').prop("checked",false);
    }
});
$(document).on('change', '#poweroff', function(){    
    if($(this).is(':checked')){
        $('#diskandmemory').prop("checked",false);
        $('#quiesced').prop("checked",false);
    }
});

$(document).on('change', '#revertRestoreOpt', function(){    
    if($(this).is(':checked')){
        $('#restoreCopyOpt').prop("checked",false);
    }else{
        $('#restoreCopyOpt').prop("checked",true);
    }
});
$(document).on('change', '#restoreCopyOpt', function(){    
    if($(this).is(':checked')){
        $('#revertRestoreOpt').prop("checked",false);        
    }
});

$(document).on('change', '#jobnotify', function(){    
    if($(this).is(':checked')){
        $('#jobnotifyonlyonerrors').removeAttr('disabled');        
    }else{
        $('#jobnotifyonlyonerrors').prop("checked",false);
        $('#jobnotifyonlyonerrors').attr('disabled', 'disabled');
    }
});
$(document).on('change', '#gfsEnable', function(){
    if($(this).is(':checked')){        
        $('.gfsGroup').removeAttr('style');
	if($('#vaultEnable').is(':checked') ){
		$('.gfsGroupVault').removeAttr('style');
	}
    }else{
        $('.gfsGroup').attr('style', 'display:none;');
	if($('#vaultEnable').is(':checked') ){
		$('.gfsGroupVault').attr('style', 'display:none;');
	}
    }
});
$(document).on('change', '#vaultEnable', function(){
    if($(this).is(':checked') && $('#gfsEnable').is(':checked') ){
        $('.gfsGroupVault').removeAttr('style');
    }else{
        $('.gfsGroupVault').attr('style', 'display:none;');
    }
});

$(document).on('change', '#validateInJob', function(){    
    if($(this).is(':checked')){
        $('#stopValidateDuringMunge').prop("checked",false);
        $('#stopValidateDuringMunge').change();
    }
});
$(document).on('change', '#stopValidateDuringMunge', function(){    
    if($(this).is(':checked')){
        $('#validateInJob').prop("checked",false);
        $('#validateInJob').change();
    }
});


//$(document).on('change', '#gfsProfileList', function(){
//    var key=$(this).val(); 
//    var args= new Object();
//    args["id"] = key;
//    var msg ="";    
//	wsCall("gfsProfile",args,"updateGFSData",null);
//});


$(document).on('change', '#selectBykeyword', function(){    
    if($(this).is(':checked')){
        $("#keywordSelectionDiv").css("display","block");
        $("#manualSelectionDiv").css("display","none");
    }else{
        $("#keywordSelectionDiv").css("display","none");        
    }
});
$(document).on('change', '#selectManually', function(){    
    if($(this).is(':checked')){
        $("#manualSelectionDiv").css("display","block");        
        $("#keywordSelectionDiv").css("display","none"); 
    }else{
        $("#manualSelectionDiv").css("display","none");
    }
});

$(document).on('change', '#smtpUsername', function(){    
    if($(this).val()){        
        $('#useSMTPAuth').prop("checked",true);
        updateSetting("useSMTPAuth", "true");
    }
});

$(document).on('change', '#blockEncryption', function(){    
    if($(this).is(':checked')){
        $("#encPassDiv").removeClass("hidden");
    }else{
        $("#encPassDiv").addClass("hidden");
    }
});

$(document).on('keyup', '#filterBUstr', function(){
    $(".vmDiv").parent().removeClass("hidden-force");
    $(".ui-accordion-header").removeClass("hidden-force");
    $(".hostPickerItem").removeClass("hidden-force");
    
    var s = $("#filterBUstr").val().toUpperCase();
    $('#hostPicker').find('.vmDiv').each(function(){
        var txt = $(this).text().toUpperCase();
        var hide = true;
        if(txt.indexOf(s) > -1){
            hide = false;
        }
        if(hide==true){
            $(this).children('input').each(function () {
                if(this.name= 'vmuuid'){
                    var name = this.value.toUpperCase();
                    if(name.indexOf(s) > -1){
                        hide = false;                    
                    }
                }
            });
        }
        if(hide==true){
            $(this).parent().addClass('hidden-force');
        }
    });
    
    // loop again to hide parent h5 containers
    $('#hostPicker').find('.selectableItems').each(function(){
        var kids = $(this).children().filter(function() {
                  if( $(this).hasClass('hidden-force') ){ return false;}
                  else { return true; }
                });
        var num = kids.length;
        
        if(num <=1){
            $(this).parent().addClass('hidden-force');
            $(this).parent().prev('h5').addClass('hidden-force');
        }
    });
    
});
$(document).on('keyup', '#filterR', function(){
    $(".ui-accordion-header").removeClass("hidden-force");
    
    var s = $("#filterR").val().toUpperCase();
    $('#backupPicker').find('.ui-accordion-header').each(function(){
        var txt = $(this).text().toUpperCase();
        var hide = true;
        if(txt.indexOf(s) > -1){
            hide = false;
        }        
        if(hide==true){
            $(this).addClass('hidden-force');
        }
    });
});


$(document).on('keyup', '#filterWizStr', function(){
    $(".instWizRow").removeClass("hidden-force");

    var s = $("#filterWizStr").val().toUpperCase();
    $('#vmListDiv').find('.instWizRow').each(function(){
        var txt = $(this).data("id").toUpperCase();
        var hide = true;
        if(txt.indexOf(s) > -1){ hide = false; }
        if(hide==true){
            $(this).addClass('hidden-force');
        }
    });
});



$(document).on('click', '#testKeyword', function(){
    if($("#keywordTag").is(':checked')){
        var args= new Object();
        args["q"] = $("#policyKeyword").val();
	wsCall("vms-tag",args,"listJobKeywordTest",null);
    }else{
        var args= new Object();
        args["q"] = $("#policyKeyword").val();
        args["showType"] = 0;
	wsCall("vms",args,"listJobKeywordTest",null);
    }
});


$(document).on('click', '#saveScheduleBtn', function(){
        if(saveSchedule()){
            $("#schedule-message").text("Saving now...");
            $("#schedule-message").addClass("modal-success");
            listSchedules();
        }else{
            $("#schedule-message").text("Job NOT saved");
            $("#schedule-message").addClass("modal-error");
        }
});

$(document).on('change', '#targetHost', function(){
    var sel=$(this).val();
    buildHostSRList(0,sel, "targetSR");
});

$(document).on('click', '#serviceState', function(e){    
    var state = $(this).attr('class');
    if (state == "service-state-off") { 
            startBKS();
    }else if (state == "service-state-on") { 
        stopBKS();
    }
});


$(document).on('click', '#testQHB', function(){
    var vmid = $("#vmid").val();    // this could be 0 if we've not encountered it yet
    var vmuuid =$("#vmuuid").val();
    var ap = $("#vmAuthProfile").val();
    if($("#auto-accessip").is(':checked')){
    
    }else{
        var ip =$("#accessip").val();    
    }
    
    var args= new Object();
    args["vmid"] = vmid;
    args["uuid"] = vmuuid;
    args["authProfile"] = ap;
    args["virtType"] = "10";
    if(ip){
        args["accessip"] = ip;  // if omitted, then auto
    }
    
	wsCall("testAgent",args,null,null);
});

$(document).on('click', '#testHost', function(){    
    $("#modal-error").removeClass();    
    $("#modal-error").html("Testing access now...");
    var args= new Object();
    var vt = $("#virtType").val();
    if(!vt){
        vt =$('input[name=virtType]:checked').val();
    }
    args["virtType"] = vt;
    if(vt == "10"){
        var ip =$("#accessIP").val();
        if(!ip || ip=="" ){
            $("#modal-error").addClass("modal-error");
            $("#modal-error").html("Please provide a valid Access IP Address.");
            return;
        }
        args["ip"] = ip;
    }else if(vt == "3"){
        var ip =$("#hostname").val();
        if(!ip || ip=="" ){
            $("#modal-error").addClass("modal-error");
            $("#modal-error").html("Please provide a valid Host Address.");
            return;
	}
        args["ip"] = ip;
    }else{
        var ip =$("#hostname").val();
        var user = $("#username").val();
        var pass = $("#password").val();
        if(!ip || ip=="" || user =="" || pass ==""){
            $("#modal-error").addClass("modal-error");
            $("#modal-error").html("Please provide all values.");
            return;
        }
        args["ip"] = ip;
        args["username"] = user;
        args["password"] = pass;
	args["useSSL"] =  "1";
    }
	wsCall("testAgent",args,"hostTestResults",null);
});

$(document).on('click', '.support-archive', function(){
    var url = '/ws/0/support/package';
    var tmp = new tempForm(url);
    tmp.submit();
});


$(document).on('click', '.trashable', function(){    
    var $target = $(this).closest("li");            
    $target.remove();
});
$(document).on('click', '.trashable_v', function(){    
    var $target = $(this).closest("li");            
    $target.remove();
});

$(document).on('click', '#showHideBP', function(){
    if($("#blockPassword").attr('type')=='password' ){
        $("#blockPassword").attr('type','text')
    }else{
        $("#blockPassword").attr('type','password');
    }
});
$(document).on('click', '#signin-password-label', function(){
    if($("#signin-password").attr('type')=='password' ){
        $("#signin-password").attr('type','text')
        $('#signin-password-label').text(getText('hide'));
    }else{
        $("#signin-password").attr('type','password');
        $('#signin-password-label').text(getText('show'));
    }
});

$(document).on('click', '#close-jld-vms', function(){
    var fade = { opacity: 0, transition: 'opacity 0.1s' };
    $(".jld_table").css(fade);
    $(".jld_table").slideUp();
    window.hiddenJLDs= ['all'];
    window.showJLDs=[];
});
$(document).on('click', '#show-jld-vms', function(){
    var fade = { opacity: 1, transition: 'opacity 0.1s' };
    $(".jld_table").css(fade);
    $(".jld_table").slideUp();
    window.hiddenJLDs = [];
    window.showJLDs=[];
});

$(document).on('click', '.jld_header', function(){
    
    var t = $(this).next('table');
    if(t.is(":visible")){
        var fade = { opacity: 0, transition: 'opacity 0.1s' };
        t.css(fade);
        t.slideUp();
        var id = $(this).attr('id');
        if(!id ){ id ="0"; }
        if(!window.hiddenJLDs){ window.hiddenJLDs = [id]; }
        else{window.hiddenJLDs.push(id); }
        if(inArr(id, window.showJLDs)){
            window.showJLDs= jQuery.grep(window.showJLDs, function(value) {
                return value != id;
            });
        }
    }else{
        var poof = { opacity: 1, transition: 'opacity 0.1s' };
        t.css(poof);
        t.slideDown();
        var id =$(this).attr('id');
        if(!id ){ id ="0"; }
        if(!window.showJLDs){ window.showJLDs = [id]; }
        else{window.showJLDs.push(id); }
        
        if(window.hiddenJLDs){            
            window.hiddenJLDs= jQuery.grep(window.hiddenJLDs, function(value) {
                return value != id;
            });
        }
        
    }
});


$(document).on('click', '.vdt_click', function(){
    var id = $(this).attr('id').substring(4);
    
    var div = "#vdisk_"+id;
    
    if($(div).is(":visible")){
        $(this).addClass('vdt_dn');
        $(this).removeClass('vdt_up');
        var fade = { opacity: 0, transition: 'opacity 0.1s' };
        $(div).css(fade);
        $(div).slideUp();        
    }else{
        $(this).removeClass('vdt_dn');
        $(this).addClass('vdt_up');
        var poof = { opacity: 1, transition: 'opacity 0.1s' };
        $(div).removeClass('hidden');
        $(div).css(poof);
        $(div).slideDown();
        
        var s = $.trim($(div).html());
        if(s =='' ){
            $(div).html("<b>Please wait, loading drives...</b>");
            var args= new Object();
            args["vmuuid"] = id;        
		wsCall("disks",args,"updateVMDisks",null);
        }
    }
    
});

$(document).on('click', '#redeployAllABDs', function(){    
    deployAllABDs();
});

$(document).on('click', '#cullABDs', function(){    
    var args= new Object();
	args["id"]=0;
	wsCall("abd/cull",args,null,null);
});

$(document).on('click', '.go_dash', function(){
	drawDash();
});
$(document).on('click', '.go_insta', function(){
	listInstaboots();
	if(window.subInfo.edition !=2 && window.drWarnBoot !=1){
		doToast("InstaBoot restores require Alike DR Edition.<br>To upgrade, please contact sales, or your local partner", "Alike Edition requirement not met", "warning");
		window.drWarnBoot=1;
	}
});

$(document).on('click', '.go_joblog', function(){
	var id = $(this).data("id");
	var site = $(this).data("site");
	window.viewingJLD=true;
	getJobDetails(id, site);
});
$(document).on('click', '.go_jobs-run', function(){
	getActiveJobs();
});
$(document).on('click', '.go_jobs-search', function(){
	var offset = $(this).data("id");
	var limit = 10;
	getJobHistory(offset,limit);
});
$(document).on('click', '.go_jobs-hist', function(){
	getJobHistory(0,20);
});
$(document).on('click', '.go_vms', function(){
	wsCall("vms", null, "drawAllSystemsTable", null)
});
$(document).on('click', '.go_agents', function(){
	wsCall("agents", null, "drawAgentsTable", null);
});
$(document).on('click', '.go_schedules', function(){
	showLineLoad();
	drawSchedulesTable();
});
$(document).on('click', '.go_sched-new', function(){
	handleEditSchedule(null, 0);
});
$(document).on('click', '.sched-type-new', function(){
	var tp = $(this).data("type");
//	var a3 = getA3FromGuid($("#a3id").val() );
	var a3id = $("#a3id").val();
	if(tp == "backup"){
		createNewBackup(a3id);
	}else if(tp == "restore"){
		createNewRestore(a3id);
	}else if(tp == "replicate"){
		// TODO: ONLY DR EDITION
		if(window.subInfo.edition !=2 && window.drWarnRep !=1){
			doToast("The replication feature requires Alike DR Edition.<br>To upgrade, please contact sales, or your local partner", "Alike Edition requirement not met", "warning");
			window.drWarnRep=1;
		}else{
			createNewReplicate(a3id);
		}
	}
});
$(document).on('click', '.go_backup-hist', function(){
	wsCall("backups", null, "drawBackupsTable", 0)

});
$(document).on('click', '.go_vault-hist', function(){
	wsCall("backups", null, "drawBackupsTable", 1)
	if(window.subInfo.edition !=2 && window.drWarnVault !=1){
		doToast("Offsite Vaulting requires Alike DR Edition.<br>To upgrade, please contact sales, or your local partner", "Alike Edition requirement not met", "warning");
		window.drWarnVault=1;
	}
});
$(document).on('click', '.go_sys-prot', function(){

	var which = sessionStorage.getItem("show_only_vaulted");
        if(which && which ==1){ 
		listVMs(2);
	}else{
		listVMs(1);
	}


});
$(document).on('click', '.go_sys-exp', function(){
    listVMs(0);
});
$(document).on('click', '.go_bak-exp', function(){
    listBackups("recent");
});
$(document).on('click', '.go_a3s', function(){
	if(!isManager()){
		showLineLoad();

		var a3s = JSON.parse(sessionStorage.getItem("a3Cache"));
		if(a3s.length==0){
			doToast("No A3s defined in local DB.  Please contact support for assistance.", "Configuration error", "error");
		}else{
			var id = a3s[0].id;
			wsCall("a3-details", null, "drawA3Details", id, id);
		}
	}else{
		listA3s();
	}
});
$(document).on('click', '.go_a3-details', function(){
	showLineLoad();
	var id = $(this).data("id");
	wsCall("a3-details", null, "drawA3Details", id, id);
});
$(document).on('click', '.go_hosts', function(){
	wsCall("hosts", null, "drawHostsTable", null);
});
$(document).on('click', '.go_manage_a3s', function(){
	listA3s();
});
$(document).on('click', '.go_abds', function(){
	showLineLoad();
	wsCall("a3s", null, "drawABDsTable", null)
});
$(document).on('click', '.go_log', function(){
    viewLogs();
});
$(document).on('click', '.go_help', function(){
	drawSupport();
});
$(document).on('click', '.go_settings', function(){
	wsCall("settings", null, "drawSettings", null);
});
$(document).on('click', '.go_subscription', function(){
	wsCall("settings", null, "drawSubscription", null);
});

$(document).on('click', '.quick-restore', function(){
	var name = $(this).data("name");
	var uuid = $(this).data("uuid");
	var ts = $(this).data("ts");
	var site = $(this).data("site");
	var ds = $(this).data("ds");
	showQuickRestore(name, uuid, ts, site, ds);
});

$(document).on('click', '#nav-help-gsg', function(){
    loadFrame('GSG');
});
$(document).on('click', '#nav-help-ag', function(){
    loadFrame('ADMINGUIDE');
});
$(document).on('click', '#nav-help-kb', function(){
    loadFrame('KB');
});
$(document).on('click', '#nav-help-support', function(){
    viewSupport();
});


$(document).on('click', '.closeX', function(){
    toggleDetailsPane(false);
});
$(document).on('click', '.closeGenericModal', function(){
   displayGenericModal(false); 
});
$(document).on('click', '.closeDetailModal', function(){
	displayDetailModal(false); 
});
$(document).on('click', '.closeTopModal', function(){
   displayTopModal(false); 
});
$(document).on('click', '.closeWelcomeModal', function(){
   displayWelcomeModal(false); 
});
$(document).on('click', '.openAP', function(){
	manageAuthProfiles();
});
$(document).on('click', '.openGFS', function(){
	manageGFSProfiles();
});
$(document).on('click', '.showWelcome', function(){
	showWelcome();
});
$(document).on('click', '.showNoConnection', function(){
	showNoConnection();
});
$(document).on('click', '.showNoDBs', function(){
	showNoDBs("No message"); 
});
$(document).on('click', '.showNoLic', function(){
	showNoLicense(); 
});

$(window).resize(function() {
        // do stuff
        if($('#detailsPane').is(':visible')) { 
            resizeDetailsPane();
        }
});


$(document).on('change', '#siteid-toggle', function(){
	if($('#siteid-toggle').is(':checked')){
		// ads
		browseFlr(0,'/');
	}else{
		// ods
		browseFlr(1,'/');
	}
});

$(document).on('click', '.collapse-arrow', function(){
	var targ = $(this);
	if (!$(this).is('i')) {
		targ = $(this).find('i:first');
	}
	if(!targ.length){ 
		return; 
	}

	if(targ.hasClass('fa-angle-right')){
		targ.toggleClass('fa-angle-right fa-angle-down');	
	}else{
		targ.toggleClass('fa-angle-down fa-angle-right');	
	}
});
$(document).on('change', '#toggle-trace', function(){
	if($('#toggle-trace').is(':checked')){
		setCookie("hide-trace", true, 9000);
		$('tr.trace').hide();
	}else{
		setCookie("hide-trace", false, 9000);
		$('tr.trace').show();
	}
});


$(document).on('click', '#navbar-vis', function(){
	var cval = false;
	if($('body').hasClass('sidebar-collapse')){
		cval = true;
	}
	setCookie("hide-sidebar", cval, 9000);

});
$(document).on('click', '#theme-switch', function(){
	var cval = true;
	if($('body').hasClass('dark-mode')){
		cval = false;
	}else{
		cval = true;
	}
	setDarkMode(cval);
});

function setDarkMode(mode){
	setCookie("dark-mode", mode, 9000);
	if(mode){
		$('body').addClass('dark-mode');
	}else{
		$('body').removeClass('dark-mode');
	}
}

$(document).on('click', '.job-cancel', function(){
	var id = $(this).data("id");
	var site = $(this).data("site");
	cancelJob(id, site);
});
$(document).on('click', '.drop-button', function(e){
	e.stopPropagation();
	targ = $(this).next('.dropdown-menu');
	targ.show();
});
$(document).click(function(){
	$(".dropdown-menu-a3").hide();
});


//$(document).on('keyup', '#schedule-search', function(){
//    var s = $("#schedule-search").val().toUpperCase();
//	if(s.length > 0){
//		$(".schedule-entry").addClass("hidden");
//	}else{
//		$(".schedule-entry").removeClass("hidden");
//		return;
//	}
//    $('#schedule-table').find('.searchable').each(function(){
//        var txt = $(this).text();
//	txt = txt.toUpperCase();
//        if(txt.includes(s) ){
//            $(this).closest('tr').removeClass('hidden');
//	}
//    });
//});

$(document).on('click', 'tr.expando', function(){
	$(this).nextUntil('tr.expando').slideToggle(10);
});
$(document).on('click', 'div.expando', function(){
	$(this).nextUntil('div.expando').slideToggle(100);
});
$(document).on('click', '.nav-link', function(e){
	if($(this).hasClass("nav-category")){ 
		const clicked = $('.nav-treeview').index(this);
		$('.nav-treeview').each(function(index) {
		    if (index !== clicked) { 
//			$(this).collapse('hide'); 
			}
		});

//		$('.nav-treeview').collapse();
//		$('.nav-treeview').hide();
		return; 
	}
	$(".nav-link").removeClass("active");
	$(this).addClass("active");
});


///////////////// ABD Functions

// this little helper pulls the info from the correct site/panel
function getAbdInf(cnt, site, inf){
	var start = $("#a3-"+site+"-"+cnt);
	if(inf == "pool"){ return start.data("pool"); }
	return start.find(inf);
}

$(document).on('click', '.abd-use-dhcp', function(evt) { abdUseDhcp($(evt.currentTarget)); });
async function abdUseDhcp(guy){
	var site = guy.data("site");
	var cnt = guy.data("cnt");

	var pool = getAbdInf(cnt, site, "pool");
	var xennet = getAbdInf(cnt, site,".abd-vnetwork").val();

	$("#manual-ip-"+cnt).toggle();
	var dhcp = false;
	if(guy.is(':checked')){ dhcp = true; }

	var args= new Object();
	args["poolid"] = pool;
	args["xennet"] = xennet;
	if(dhcp){
		args["useDHCP"] = "1";
	}else{
		args["useManualIP"] = "1";
	}

	try{ 
		var res = await wsPromise("abd/setDHCP", args, site);
	} catch (ex){
		doToast(ex.message, "Failed to change DHCP settings", "error"); 
		$("#manual-ip-"+cnt).toggle();
		guy.prop('checked', !guy.prop('checked'));
	}
}


$(document).on('click', '.abd-ip-add', function(evt) { abdAddIp($(evt.currentTarget)); });
async function abdAddIp(guy){
	var site = guy.data("site");
	var cnt = guy.data("cnt");
	var pool = getAbdInf(cnt, site, "pool");

	// clear any previous errors
	var ipguy = getAbdInf(cnt, site, ".abd-ip");
	var gwguy = getAbdInf(cnt, site, ".abd-ip");
	ipguy.removeClass("is-invalid");
	gwguy.removeClass("is-invalid");

	var gw = getAbdInf(cnt, site, ".abd-gw").val();
	var mask = getAbdInf(cnt, site, ".abd-ip-mask").val();
	var ip = ipguy.val();
	if(!checkIP(ip)){
		ipguy.addClass("is-invalid");
		return;
	}
	if(!checkIP(gw)){
		gwguy.addClass("is-invalid");
		return;
	}
	var xennet = getAbdInf(cnt, site,".abd-vnetwork").val();
	var dhcp = getAbdInf(cnt, site, ".abd-use-dhcp").val();
	var net = CIDRToMask(mask);
        var mac = generateMAC();

	var args= new Object();
	if(dhcp){
		args["useDHCP"] = "1";
	}
	args["useManualIP"] = "1";
	args["ip"] = ip
	args["netmask"] = CIDRToMask(mask);
	args["gw"] = gw;
	args["MAC"] = mac
	args["xennet"] = xennet;
	args["poolid"] = pool
	try{
		var res = await wsPromise("abdnet/set", args, site)
		var row = makeAbdNetRow(ip, CIDRToMask(mask), gw, cnt, 0, site);	
		$("#abd-ips-"+cnt).append(row);
	}catch (ex){
		doToast(ex.message, "Failed to update ABD IP", "error");
	}
}

$(document).on('click', '.abd-ip-del', function(evt){ abdDelIp($(evt.currentTarget)); });
async function abdDelIp(guy){
	var netid = guy.data("id");
	var site = guy.data("site");
	var cnt = guy.data("cnt");
	var pool = getAbdInf(cnt, site, "pool");
	var args= new Object();
	args["id"] = netid;
	try{
		var res = await wsPromise("abdnet/delete", args, site)
		guy.closest('tr').remove();		
	}catch (ex){
		doToast(ex.message, "Failed to remove ABD IP", "error");
	}
}

$(document).on('change', '.abd-vnetwork', function(){
	var net = $(this).val();
	var site = $(this).data("site");
	var cnt = $(this).data("cnt");
	var pool = getAbdInf(cnt, site, "pool");
	var args= new Object();
	args["poolid"] = pool;
	args["xennet"] = net;
	//wsCallGzSite("abd/setnet",args,"Xen Net Set","Could not set Xen Net", null,null,false, site);
	wsCall("abd/setnet", args, null,null, site );
});
$(document).on('click', '.abd-diag', function(){
	var site = $(this).data("site");
	var pool = $(this).data("pool");
	var args= new Object();
	args["poolid"] = pool;
	wsCall("abd/diag", args, null,null, site );
	doToast("ABD Diagnostic job has started.<br>Please check your active jobs for details.","ABD Diag started", "success");
});
$(document).on('click', '.abd-cull', function(){
	var site = $(this).data("site");
	var abdid = $(this).data("id");
	var args= new Object();
	args["id"] = abdid;
	wsCall("abd/cull", args, null,null, site );
	$(this).closest('tr').remove();		
	doToast("Removing ABD.","ABD Cleanup started", "success");
});
$(document).on('click', '.abd-cull-idle', function(){
        var site = $(this).data("site");
        var pool = $(this).data("pool");
        var args= new Object();
        args["poolid"] = pool;
        wsCall("abd/cull-idle", args, null,null, site );
        doToast("Removing any idle ABDs.","ABD Cleanup started", "success");
});

$(document).on('click', '.abd-import', function(){
        var site = $(this).data("site");
        var pool = $(this).data("pool");
        var args= new Object();
        args["poolid"] = pool;
        wsCall("abd/import", args, null,null, site );
        doToast("Importing ABD template to pool.","ABD Import started", "success");
});

$(document).on('click', '.deploy-abd', function(){
	var id = $(this).data("id");
	deployABD(id );
});
$(document).on('click', '.edit-abd-ip', function(){
	var id = $(this).data("id");
	editABDIPs(id );
});

///////////////// End ABD Functions
//
$(document).on('change', '#vm-vaults', function(){
	var uuid = $(this).data("uuid");
	if($('#vm-vaults').is(':checked')){
		var args= new Object();
		args["vaults"] = 1;
		wsCall("backups", args, "drawVmDetails", uuid);
	}else{
		var args= new Object();
		args["backups"] = 1;
		wsCall("backups", args, "drawVmDetails", uuid);
	}
});

$(document).on('change', '#a3-minilog-drop', function(){
	$("#a3-minilog").html("<center> <div><h4>Loading...</h4><br></div> <div class='mexican-wave' ></div> </center> ");
        var site = $(this).data("site");
        var log = $(this).val();
	if(log == "alerts"){
		let args= new Object();
		args["id"] = site;
		wsCall("alerts/getFrom", null, "updateMinilog", null, 0 );

	}else{
		wsCall("logsummary/"+log, null, "updateMinilog", null, site );
	}
});

$(document).on('focus', '.auto-setting', function(){
	if ($(this).is('input[type="text"]')) {
		$(this).data("prev", $(this).val().trim() );
	}
});
$(document).on('change', '.auto-setting', function(){
	if ($(this).attr('id') === undefined || $(this).attr('id') === null) { return; }
        var site = $(this).data("site");
        var set = $(this).attr("id");

	if ($(this).is('input[type="text"]')) {
		if($(this).data("prev") == $(this).val().trim() ){ return; } // unchanged
		if ($(this).val().trim() === '') {
			$(this).val($(this).data("prev"));
			return;
		}
		updateSetting(set, $(this).val().trim(), site);
		if($(this).data("name") == "a3name"){
			var args= new Object();
			args["id"] = site;
			args["name"] = $(this).val().trim();
			wsCall("a3/set", args, null, null, 0);
		}

	}else if ($(this).is(':checkbox')) {
		if($(this).is(':checked')){
			updateSetting(set, true, site);
		}else{
			updateSetting(set, false, site);
		}
	}
});
$(document).on('change', '.test-email-toggler', function(){
	if($(this).is(':checked')){
		$("#test-email-pane").show();	
	}else{
		$("#test-email-pane").hide();	
	}
})
$(document).on('click', '#send-test-email', function(){
        var site = $(this).data("site");
	wsCall("testemail", null, "testEmailCallback", null, site);
});
$(document).on('click', '.download-log', function(){
        var site = $(this).data("site");
	var log = $("#a3-minilog-drop").val();
	var url = '/ws/'+site+'/logs/'+log;
	var tmp = new tempForm(url);
	tmp.submit();
});


$(document).on('click', '.quick-restore-start', function(){
	let site  = $("#vm-data").data("site");
	let destSr = $("#dest-sr").val();
	let destVhd = $("#dest-vhd").val();
	let destUuid = $("#dest-uuid").val();
//	let template = $("#dest-template").val();
	let template = 'Other install media';
	let net = $("#dest-network").val();

	var args= new Object();
	let disks = [];
	let allChecked = $(".quick-rest-disk").map(function() { return $(this).prop('checked'); }).get().every(Boolean);
	if(!allChecked){
		$(".quick-rest-disk").each(function() { 
			if($(this).prop('checked')){ disks.push($(this).data("diskid") ); }
		});
		if(disks.length==0){
			doToast("No disks selected!.  Please include at least 1 disk to restore.", "Nothing to restore!", "warning");
			return;
		}
		args["disks"] = disks;
	}

	args["destUuid"] = destUuid;
	args["vmUuid"] = $("#vm-data").data("uuid");
	args["ds"] = $("#vm-data").data("ds");
	args["ts"] = $("#vm-data").data("ts");
	args["sr"] = destSr;
	args["vhd"] = destVhd;
	args["template"] = template;
	args["network"] = net;
	wsCall("restore/quick",args,"quickRestoreCallback",null, site );
	$("#modal-ok-button").removeClass('quick-restore-start');
});

$(document).on('click', '.go_backup-explorer', function(){
	var site = 0; // GET THE FIRST A3 ID
	let cache = JSON.parse(sessionStorage.getItem("a3Cache"));
	if(cache.length > 0){
		site = cache[0].id;
		browseFlr(site, 0, '/');
	}
});
$(document).on('change', '#flr-site', function(){
	var site = $("#flr-site").val();
	browseFlr(site,0,'/');
});
$(document).on('change', '#flr-ds', function(){
	let ds = 0;
	if($('#flr-ds').is(':checked')){ ds=1; }
	var site = $(this).data("site");
	let relpath = '/'; 
	browseFlr(site,ds,relpath);
});
$(document).on('click', '.flr_folder', function(){
	var site = $(this).data("site");
	var ds = $(this).attr("data-ds");
	var relpath = $(this).attr("data-path");
	if (!relpath.endsWith('/')) { relpath += '/'; }
	relpath  += $(this).attr("data-name");
	browseFlr(site,ds,relpath);
});
$(document).on('click', '.flr_file', function(){
	var ds = $(this).data("ds");
	var path = $(this).data("path") + $(this).data("name");
	var site = $(this).data("site");

	var args= new Object();
	args["ds"] = ds;
	args["path"] = path;
	wsDownload("flrDownload", args, site);
	return;

	var url = 'flrDownload?session='+window.currentSessionID +'&file='+path+'&ds='+ds;
	var tmp = new tempForm(url);
	tmp.submit();
	//downloadFile(url, $(this).attr("data-name") );
});
$(document).on('click', '.flr-browse-version', function(){
	var site = $(this).data("site");
	var ds = $(this).data("ds");
	var uuid = $(this).data("uuid");
	var ts = $(this).data("ts");
	showVmInFlr(site, ds, uuid, ts);
});

$(document).on('click', '.delete-backup', function(evt) { deleteBackup(evt); });
async function deleteBackup(evt){
	let guy = $(evt.currentTarget);
	var uuid = guy.data("uuid");
	var vers = guy.data("ts");
	var site = guy.data("site");
	var ds = guy.data("ds");

	var args= new Object();
	args["ds"] = ds;
	args["uuid"] = uuid;
	args["ts"] = vers;
	try{
		showLineLoad();
		var res = await wsPromise("version/delete", args, site)
		guy.closest('tr').remove();		
	}catch (ex){
		doToast(ex.message, "Failed to delete Version", "error");
	}
	finishLineLoad();
}

$(document).on('click', '.vault-backup', function(evt) { manualVault(evt,0); });
async function manualVault(evt,ds){
        let guy = $(evt.currentTarget);
        var uuid = guy.data("uuid");
        var vers = guy.data("ts");
        var site = guy.data("site");

        var args= new Object();
        args["ds"] = ds;
        args["uuid"] = uuid;
        args["ts"] = vers;
        try{
                showLineLoad();
                var res = await wsPromise("vaulting/manualVault", args, site)
        }catch (ex){
                doToast(ex.message, "Failed to start vault", "error");
        }
        finishLineLoad();
}


$(document).on('click', '.reverse-vault', function(evt) { manualVault(evt,1); });

$(document).on('click', '.agent-delete', function(evt) { 
	let targ = $(evt.currentTarget);
        $.confirm({
                content: "Delete this Agent from the database?<br><br>Please note, locally installed agent software must be uninstalled manually.",
                title: "Confirm Agent removal",
                draggable: true,
                closeIcon: false,
                icon: 'far fa-question-circle',
                buttons: {
                    confirm: function() {
			let guy = targ.closest("tr");
			let guid = targ.data("guid");
			agentDelete(guid, guy);
                    },
                    cancel: function() { }
                }
        });
});

//$(document).on('click', '.agent-delete', function(evt) { agentDelete(evt); });
async function agentDelete(guid, parentRow){
        var args= new Object();
        args["guid"] = guid;
        try{
                var res = await wsPromise("agent/delete", args, 0)
                doToast(res.message, "Agent removed", "success");
		parentRow.remove();            
        }catch (ex){
                doToast(ex.message, "Failed to remove agent", "error");
        }
}

$(document).on('click', '.agent-license', function(evt) { agentLicense(evt); });
async function agentLicense(evt){
        let guy = $(evt.currentTarget);
        var args= new Object();
        args["guid"] = guy.data("guid");
	let call = 'agent/unlicense';
	let msg = 'Agent unlicensed';
        if(guy.is(':checked')){
		call = 'agent/license';
		msg = 'Agent licensed';
        }
        try{
                var res = await wsPromise(call, args, 0)
                wsCall("licenses", null, "populateLicensesPanel", null);
                doToast(res.message, msg, "success");
        }catch (ex){
                doToast(ex.message, "Failed to modify licensing", "error");
                guy.prop('checked', !guy.prop('checked'));
        }
        //wsCall("host/license", args,null, null);
}

$(document).on('click', '.agent-license-old', function(evt) { agentLicenseOld(evt); });
async function agentLicenseOld(evt){
        let guy = $(evt.currentTarget);
        var args= new Object();
        args["guid"] = guy.data("uuid");
        try{
                var res = await wsPromise("agent/license", args, 0)
		guy.removeClass('badge-warning');
		guy.removeClass('agent-license');
		guy.addClass('badge-success');
		guy.addClass('agent-unlicense');
		guy.html('Licensed');
                doToast(res.message, "Agent licensed", "success");
                wsCall("licenses", null, "populateLicensesPanel", null);
        }catch (ex){
                doToast(ex.message, "Failed to modify licensing", "error");
        }
}
$(document).on('click', '.agent-unlicense', function(evt) { agentUnlicense(evt); });
async function agentUnlicense(evt){
        let guy = $(evt.currentTarget);
        var args= new Object();
        args["guid"] = guy.data("uuid");
        try{
                var res = await wsPromise("agent/unlicense", args, 0)
                guy.removeClass('badge-success');
		guy.removeClass('agent-unlicense');
                guy.addClass('badge-warning');
		guy.addClass('agent-license');
                guy.html('Not Licensed');
                doToast(res.message, "Agent unlicensed", "success");
        }catch (ex){
                doToast(ex.message, "Failed to modify licensing", "error");
        }
}


$(document).on('click', '.host-license', function(evt) { hostLicense(evt); });
async function hostLicense(evt){
        let guy = $(evt.currentTarget);
        var args= new Object();
        args["license"] = 0;
        args["guid"] = guy.data("guid");
	if(guy.is(':checked')){
		args["license"] = 1;
	}
        try{
                var res = await wsPromise("host/license", args, 0)
		wsCall("licenses", null, "populateLicensesPanel", null);
        }catch (ex){
                doToast(ex.message, "Failed to modify licensing", "error");
		guy.prop('checked', !guy.prop('checked'));
        }
	//wsCall("host/license", args,null, null);
}

$(document).on('click', '.host-delete', function(evt) { hostDelete(evt); });
async function hostDelete(evt){
        let guy = $(evt.currentTarget);
        var guid = guy.data("guid");

        var args= new Object();
        args["guid"] = guid;
        try{
                showLineLoad(); 
                var res = await wsPromise("host/delete", args, 0)
                guy.closest('tr').remove();
        }catch (ex){
                doToast(ex.message, "Failed to remove host", "error");
        }
        finishLineLoad();
}

$(document).on('change', '.host-a3', function(){
        let guy = $(this);
        var hguid = guy.data("guid");
        var a3s = guy.val();

        var args= new Object();
        args["guid"] = hguid;
        args["a3s"] = a3s;
	wsCall("host/assign", args, null, null);	
});

$(document).on('click', '.add-agent', function(){
	drawAddAgentModel();
});
$(document).on('click', '#testAgent', function(evt) { testAgent(evt); });
async function testAgent(evt){
	evt.preventDefault();
	doTestAgent();
}

$(document).on('click', '#add-agent-save', function(){
        let a3 = $("#a3id").val();
        a3s = Array.isArray(a3) ? a3 : [a3];
	let a3id = a3s[0];
	var ip =$("#agent-ip").val();

        var args= new Object();
        args["ip"] = ip;
	wsCall("agent-register", args, "addAgentCallback", null, a3id);
});

///////////////////////////////////////////////// VM Detail handlers /////////////////////////////////////////////
$(document).on('click', '.vm-max-on-edit', function(evt) { editVmOn(evt); });
async function editVmOn(evt){
        let guy = $(evt.currentTarget);
	if(!$("#vm-max-on-tmp").length){
		let mx = $("#vm-max-on").html();
		if(!isNumeric(mx)){ mx = 5; }
		$("#vm-max-on").html('');
		$("#vm-max-on").append('<input type="text" class="form-control" style="display:inline-block; width: 75px;" id="vm-max-on-tmp" value="'+mx+'">');
		guy.removeClass("fa-sm");
		guy.addClass("fa-lg");
		guy.css("color", "#f5c211");
		return;
	}
	let mx = $("#vm-max-on-tmp").val();
	if(!isNumeric(mx) && mx != ""){ 
		$("#vm-max-on-tmp").addClass("is-invalid");
		return;
	}
	guy.removeClass("fa-lg");
	guy.addClass("fa-sm");
	guy.css("color", "");
	if(mx == ""){
		$("#vm-max-on").html("Global Default");
		mx =0;
	}else{ $("#vm-max-on").html(mx); }

        var args= new Object();
        args["value"] = mx;
        args["type"] = "onsiteversions";
        args["vmuuid"] = guy.data("guid");
	let a3s = JSON.parse(sessionStorage.getItem("a3Cache") );
	$.each(a3s, function(i, a3){
                wsCall("vm/set", args,null,null, a3.id);
	});
}
$(document).on('click', '.vm-max-off-edit', function(evt) { editVmOff(evt); });
async function editVmOff(evt){
        let guy = $(evt.currentTarget);

        if(!$("#vm-max-off-tmp").length){
                let mx = $("#vm-max-off").html();
                if(!isNumeric(mx)){ mx = 5; }
                $("#vm-max-off").html('');
                $("#vm-max-off").append('<input type="text" class="form-control" style="display:inline-block; width: 75px;" id="vm-max-off-tmp" value="'+mx+'">');
                guy.removeClass("fa-sm");
                guy.addClass("fa-lg");
                guy.css("color", "#f5c211");
                return;
        }
        let mx = $("#vm-max-off-tmp").val();
        if(!isNumeric(mx) && mx != ""){ 
                $("#vm-max-off-tmp").addClass("is-invalid");
                return;
        }
        guy.removeClass("fa-lg");
        guy.addClass("fa-sm");
        guy.css("color", "");
        if(mx == ""){
                $("#vm-max-off").html("Global Default");
                mx =0;
        }else{ $("#vm-max-off").html(mx); }

        var args= new Object();
        args["value"] = mx;
        args["type"] = "offsiteversions";
        args["vmuuid"] = guy.data("guid");
        let a3s = JSON.parse(sessionStorage.getItem("a3Cache") );
        $.each(a3s, function(i, a3){
                wsCall("vm/set", args,null,null, a3.id);
        });
}
$(document).on('click', '.vm-accessip-edit', function(evt) { editVmAccessIP(evt); });
async function editVmAccessIP(evt){
        let guy = $(evt.currentTarget);

        if(!$("#vm-accessip-tmp").length){
                let mx = $("#vm-accessip").html();
                if(!checkIP(mx)){ mx = ""; }
                $("#vm-accessip").html('');
                $("#vm-accessip").append('<input type="text" placeholder="Blank for default" class="form-control" style="display:inline-block; width: 150px;" id="vm-accessip-tmp" value="'+mx+'">');
                guy.removeClass("fa-sm");
                guy.addClass("fa-lg");
                guy.css("color", "#f5c211");
                return;
        }
        let mx = $("#vm-accessip-tmp").val();
        if(!checkIP(mx) && mx != ""){
                $("#vm-accessip-tmp").addClass("is-invalid");
                return;
        }
        guy.removeClass("fa-lg");
        guy.addClass("fa-sm");
        guy.css("color", "");
        if(mx == ""){
                $("#vm-accessip").html("Auto-detect");
        }else{ $("#vm-accessip").html(mx); }

        var args= new Object();
        args["value"] = mx;
        args["type"] = "accessip";
        args["vmuuid"] = guy.data("guid");
        let a3s = JSON.parse(sessionStorage.getItem("a3Cache") );
        $.each(a3s, function(i, a3){
                wsCall("vm/set", args,null,null, a3.id);
        });
}

$(document).on('click', '#gfsSave', function(evt) { setGfsProfile(evt); });
async function setGfsProfile(evt){
        let guy = $(evt.currentTarget);
	let id = $("#gfsid").val();
//	let id = guy.data("gfsid");

	let name = $("#gfsName").val().trim();
	name = name.replace(/[^a-zA-Z0-9-_ ]+/g,'');
    
	var d = $("#dailies").val().trim();
	var w = $("#weeklies").val().trim();
	var m = $("#monthlies").val().trim();
	var y = $("#yearlies").val().trim();
    
    let strict = "false";
    if($("#gfsStrict").is(':checked')){ strict="true"; }
    
    if(!name || name.length == 0){
	doToast("Please provide a GFS Profile Name!", "Missing Profile Info", "warning");
	return;
    }else if(!d || !isNumeric(d) || d <= 0){
	doToast("Please provide a valid number of daily backup retention", "Missing Profile Info", "warning");
	return;
    }else{
        var args= new Object();
        args["id"] = id;
        args["name"] = name;
        args["daily"] = d;
        args["weekly"] = w;
        args["monthly"] = m;
        args["yearly"] = y;
        
        args["weeklyDay"] = $("#weeklyPromote").val();
        args["yearlyMonth"] = $("#yearlyPromote").val();
        args["monthlyIsEndDay"] = $("#monthlyPromote").val();
        
        args["strict"] = strict;
        var msg ="GFS Profile updated successfully.";
        if(id==0){ msg = "GFS Profile added successfully."; }
		try{
			var res = await wsPromise("gfsProfile/set", args, 0)
			doToast(msg, "Profile saved", "success");
			wsCall("gfsProfiles",null, "gfsProfileList", null);
			$("#gfsName").val("");	
			$("#gfsid").val(0);
			$("#gfsSave").prop("disabled", true);
			$("#gfsDel").prop("disabled", true);
		}catch (ex){
			doToast(ex.message, "Failed to Save GFS Profile!", "error");
		}
		finishLineLoad();
    }
}

$(document).on('change', '#gfsList', function(){
	$("#gfsDel").prop("disabled", true);
        let val = $(this).val();
	if(val == -2){
		$("#gfsSave").prop("disabled", true);
		$(".gfsEdit").find("input,button,select").prop("disabled", true);
	}else{
		$(".gfsEdit").find("input,button,select").prop("disabled", false);
		$("#gfsSave").prop("disabled", false);
		if(val == -1){
			$("#gfsName").val("");	
			$("#dailies").val(10);	
			$("#weeklies").val(4);	
			$("#monthlies").val(6);	
			$("#yearlies").val(0);	
			$("#monthlyPromote").val(1);
			$("#gfsStrict").prop('checked', false);
			$("#gfsid").val(0);
		}else{
			let args=new Object();
			args["id"] = val;
			wsCall("gfsProfile",args,"populateGfsEdit",null);
			$("#gfsDel").prop("disabled", false);
			$("#gfsName").val("");	
			$("#gfsid").val(0);
		}
	}
});

$(document).on('click', '#gfsDel', function(){
	var id = $("#gfsid").val();
	var args= new Object();
	args["id"] = id;        
	wsCall("gfsProfile/delete",args,null,null);
	$("#gfsDel").prop("disabled", true);
	$("#gfsSave").prop("disabled", true);
	$("#gfsName").val("");	
	$("#gfsid").val(0);

	wsCall("gfsProfiles",null, "gfsProfileList", null);
	doToast("GFS Profile has been removed", "Profile deleted", "success");
});

///////////////////////////////////////////////////////////// Schedule add/edit handlers /////////////////////////
$(document).on('change', '#sched-freq', function(){
        let val = $(this).val();
	if(val == 0){	// daily
		$("#timepicker-panel").show();
		$("#weekdayspicker").show();
		$("#sched-intervals").show();
		$("#dayofmonth").hide();
	}else if(val == 1){	//monthly
		$("#timepicker-panel").show();
		$("#dayofmonth").show();
		$("#weekdayspicker").hide();
		$("#sched-intervals").hide();

	}else if(val ==4){	//manual
		$("#timepicker-panel").hide();
		$("#weekdayspicker").hide();
		$("#sched-intervals").hide();
		$("#dayofmonth").hide();
                if($("#schedule-save").hasClass("btn-success")){
			$("#schedule-save").removeClass("btn-success");
			$("#schedule-save").addClass("btn-primary");
			$("#schedule-save").text("Save");
		}
	}else if(val ==5){	//run now & manual (restores only)
		$("#timepicker-panel").hide();
		$("#weekdayspicker").hide();
		$("#sched-intervals").hide();
		$("#dayofmonth").hide();

		$("#schedule-save").removeClass("btn-primary");
                $("#schedule-save").addClass("btn-success");
                $("#schedule-save").text("Run Now");

	}	
});
$(document).on('change', '#use-gfs', function(){
	if($(this).is(':checked')){
		$("#use-gfs-lbl").text("Use GFS Profile");
		$("#gfsProfileList").show();
	}else{
		$("#use-gfs-lbl").text("Use Standard Retention");
		$("#gfsProfileList").hide();
	}
});
$(document).on('change', '#emailLevel', function(){
	let lvl = $(this).val();
	if(lvl ==0){
		$("#emailLevelLbl").text("No Email notifications");
	}else if(lvl==1){
		$("#emailLevelLbl").text("Email only when errors occur");
	}else{
		$("#emailLevelLbl").text("Email after every job");
	}
});
$(document).on('change', '.sched-processor', function(){
	let val = $(this).attr('id');
	if(val == "qhb"){
		$("#diskMem").prop("checked", false);
		$("#diskMem").prop("disabled", true);
	}else{
		$("#diskMem").prop("disabled", false);
	}
});
$(document).on('click', '.sched-src-item', function(){
	var uuid = $(this).data("uuid");
	if($("#src-systems").has($(this)).length  ){
		$(this).remove().appendTo("#target-systems")
		let dlist = $(this).find(".disk-list");
		if(dlist.length == 0){
			let site = $("#sched-a3id").val();
			var args= new Object();
			args["vmuuid"] = uuid;
			wsCall("disks",args,"populateDiskList",$(this),site);
		}
		dlist.show();
		$(this).find("i.sched-trash").show();
		$(this).find(".sched-disk-expand").show();
	}
	$("#keyword-tab").addClass("disabled");
	$("#wholehost-tab").addClass("disabled");
});
$(document).on('click', '.sched-trash', function(evt){
	if($("#target-systems").has($(this)).length  ){
		evt.stopImmediatePropagation();
		$(this).hide();
		$(this).parent().find(".disk-list").hide();
		let arrow = $(this).parent().find(".sched-disk-expand");
		if(!arrow.length){ 
			arrow = $(this).parent().find(".sched-disk-collapse");
		}
		arrow.removeClass("sched-disk-collapse");
		arrow.addClass("sched-disk-expand");
		arrow.hide();

		$(this).parent().find("span.disk-list").hide();
		let guy = $(this).closest("li.sched-src-item");
		guy.remove().appendTo("#src-systems")
		let ul = $("#src-systems");
		let items = ul.find("li");
		items.sort((a,b) => {
			return parseInt($(a).data("pos")) - parseInt($(b).data("pos"));
		});
		ul.empty();
		items.appendTo(ul);
	}
	if($("#target-systems").find(".sched-src-item").length ==0){
		$("#keyword-tab").removeClass("disabled");
		$("#wholehost-tab").removeClass("disabled");
	}
});

$(document).on('click', '.sched-disk-expand', function(evt){
	$(this).parent().find(".disk-list").show();
	$(this).removeClass("sched-disk-expand");
	$(this).addClass("sched-disk-collapse");
});
$(document).on('click', '.sched-disk-collapse', function(evt){
	$(this).parent().find(".disk-list").hide();
	$(this).addClass("sched-disk-expand");
	$(this).removeClass("sched-disk-collapse");
});

$(document).on('keyup', '#source-filter', function(){
    $("#src-systems .sched-src-item").show();

    var s = $("#source-filter").val().toUpperCase();
    $('#src-systems').find('.sched-src-item').each(function(i, g){
        var txt = $(this).text().trim().toUpperCase();
	let guid = $(this).data("uuid").toUpperCase();
        var hide = true;
        if(txt.indexOf(s) > -1){ hide = false; }
	if(hide){ 
		if(guid.indexOf(s) > -1){ 
		hide =false; 
		}		 
	}
        if(hide==true){
            $(this).hide();
        }
    });
});

$(document).on('click', '#schedule-save', function(evt) { 
	let which = $("#jobType").val();
	if(which==0){
		saveScheduleBackup(evt); 
	}else if(which ==1){
		saveScheduleRestore(evt); 
	}else if(which ==5){
		saveScheduleRep(evt); 
	}else{
		doToast(`Unknown Schedule type: ${which}!  Cannot save something I don't understand.`, "Error: Unknown Type", "error");
	}
});


async function saveScheduleBackup(evt){
        let guy = $(evt.currentTarget);
	let schedID = guy.data("id");
	
	showLineLoad();
        var args= new Object();
        args["id"] = schedID;
        args["name"] = $("#schedule-name").val();

        let keyword = $("#sched-keyword").val();

        args["policyType"] = 0;
	let sources =[];
	$('#target-systems li.sched-src-item').each(function() { 
		let v = new Object();
		v.uuid = $(this).data("uuid");
		v.volumes = [];
		let allChecked = $(this).find('input[type="checkbox"]').map(function() { return $(this).prop('checked'); }).get().every(Boolean);
		if(!allChecked){
			$(this).find('input[type="checkbox"]').each(function() { 
				if($(this).prop('checked')){ v.volumes.push($(this).data("id") ); }
			});
		}
		sources.push(v); 
	});
	if(sources.length ==0){
		$('#target-hosts li.sched-src-host').each(function() { 
			let uuid = $(this).data("uuid");
			sources.push(uuid); 
		});
		if(sources.length > 0 ){ args["policyType"] = 1; }
	}
	if(keyword){
		args["policyType"] = 2;
		if($("#useTags").prop('checked')){ args["policyType"] = 3; }
		args["keyword"] = $("#sched-keyword").val();
	}else if(sources.length==0) {
                doToast("No systems defined.  Please add at least 1 system to protect.", "Could not save Job!", "warning");
		finishLineLoad();
		return;
	}
        args["sources"] = sources;
        args["jobType"] = 0;

	// timing settings
        args["stype"] = $("#sched-freq").val();
        args["hour"] = $("#sched-hour").val();	
	if(args["hour"] > 23){ args["min"] = 23; }
        args["min"] = $("#sched-min").val();	
	if(args["min"] > 59){ args["min"] = 59; }
        args["interval"] = $("#sched-interval").val();
	let days =[];
	$('#weekdayspicker input[type="checkbox"]').each(function() { days.push($(this).prop('checked')); });	
        args["days"] = days;
        args["monthday"] = $("#sched-monthday").val();

        args["method"] = ($("#qhb").prop('checked')) ? "qhb" : "enh";
        args["cbt"] = ($("#cbt").prop('checked')) ? 1 : 0;
        args["diskmem"] = ($("#diskMem").prop('checked')) ? 1 : 0;
        args["forcefull"] = ($("#forceFull").prop('checked')) ? 1 : 0;
        args["concurrent"] = $("#numConcurrent").val();
        args["vault"] = ($("#enable-vault").prop('checked')) ? 1 : 0;
        args["email"] = $("#emailLevel").val();
	if($("#use-gfs").prop('checked')){
		let gfs = $("#gfsProfile").val();
		if(gfs > 0){ args["gfs"] = gfs; }
	}
	
	let site = $("#sched-a3id").val();

        try{
                var res = await wsPromise("schedule/set", args, site)
		drawSchedulesTable();
        }catch (ex){
                doToast(ex.message, "Failed to Save Schedule!", "error");
        }
	finishLineLoad();

}

$(document).on('click', '.sched-tabber', function(){
	let which = $(this).data("target");
	$(".sched-tab-content").removeClass("active");
	$(".sched-tab-content").removeClass("show");
	$("#"+which).addClass("show");
	$("#"+which).addClass("active");

	if((which == "wholehost") && $("#src-systems").find(".sched-item").length ==0){
		let site = $("#sched-a3id").val();
		var args= new Object();
		args["isHost"] =1;
		wsCall("sources",args,"populateSchedHosts",null, site);
	}
});

$(document).on('click', '.keyword-test', function(){
	let site = $("#sched-a3id").val();
	let val = $("#sched-keyword").val();

        var args= new Object();
	if($("#useTags").is(":checked")){
		args["isTag"] = 1;
	}

        args["search"] = val;
	wsCall("sources",args,"keywordTestResults",null, site);
});
$(document).on('keyup', '#sched-keyword', function(){

    var s = $(this).val().toUpperCase().trim();
	if(!s){
		$("#manual-tab").removeClass("disabled");
		$("#wholehost-tab").removeClass("disabled");
	}else{
		$("#manual-tab").addClass("disabled");
		$("#wholehost-tab").addClass("disabled");
	}
});

$(document).on('click', '.sched-src-host', function(){
        var uuid = $(this).data("uuid");
        if($("#src-hosts").has($(this)).length  ){
                $(this).remove().appendTo("#target-hosts")
                $(this).find("i.sched-host-trash").show();
        }
        $("#keyword-tab").addClass("disabled");
        $("#manual-tab").addClass("disabled");
});
$(document).on('click', '.sched-host-trash', function(evt){
        if($("#target-hosts").has($(this)).length  ){
                evt.stopImmediatePropagation();
                $(this).hide();

                let guy = $(this).closest("li.sched-src-host");
                guy.remove().appendTo("#src-hosts")
		let ul = $("#src-hosts");
		let items = ul.find("li");
		items.sort((a,b) => {
			return parseInt($(a).data("pos")) - parseInt($(b).data("pos"));
		});
		ul.empty();
		items.appendTo(ul);
	}
	if($("#target-hosts").find(".sched-src-host").length ==0){
		$("#keyword-tab").removeClass("disabled");
		$("#manual-tab").removeClass("disabled");
	}
});
///////////////////////////////////////// Replication schedule ////////////////////////////////////
$(document).on('click', '.add-rep-src', function(evt){
	var srcVms = $('#src-systems').select2('data');
	let destUuid = $("#dest-uuid").val();
	if(destUuid ==0){
                doToast("Please select your destination location and storage.", "Missing Required Info", "warning");
		return;
	}else if(srcVms.length==0){
                doToast("Please select at least 1 system to replicate.", "No systems defined", "warning");
		return;
	}
	let destName = $("#dest-uuid option:selected").text().trim().replace(/^>\s*/, '');
	let destSr = $("#dest-sr").val();
	let destSrName = $("#dest-sr option:selected").text().trim();
	let template = "Other install media";
	let destNet = $("#dest-network").val();

	let srNote = ` [${destSrName}] `;
	if($("#vhd-row").is(":visible")){ 
		srNote ="[ Default VHD Path ]"; 
		if($("#dest-vhd").val().length > 0){ 
			destSr = $("#dest-vhd").val().trim();
			srNote = `[ ${destSr} ] `;
		}
	}
	// check if the VM has already been added

	$.each(srcVms, function(i, v){
		if($("#target-systems").find(`[data-id="${v.id}"]`).length > 0){ 
			doToast(`System "${v.text}" already defined, skipping duplicated entry.`, "Skipping system", "warning");
			return; 
		}
		let trash = `<i class='fas fa-trash rep-trash clickable float-right' ></i>`;
		let targ = `
				<li data-id='${v.id}' data-destuuid='${destUuid}' data-sr='${destSr}' data-network='${destNet}' class='sched-src-item'> 
					<span><b>${v.text}</b>  <i class='fas fa-long-arrow-alt-right'></i> ${destName}  ${srNote} <i></i> ${trash}</span>
				</li>
			`;
		$("#target-systems").append(targ);
	});

	$('#src-systems').val(null).trigger('change');
	$('#dest-uuid').val(0).trigger('change');
	$('#sr-row').hide();
	$('#vhd-row').hide();
});
$(document).on('click', '.rep-trash', function(evt){
	$(this).closest('li').remove();
});
$(document).on('change', '.sched-processor-rep', function(){
	let val = $(this).attr('id');
	if(val == "cbt"){
		$("#use-gfs").prop("disabled", true);
		$("#deleteBackup").prop("checked", false);
		$("#deleteBackup").prop("disabled", true);
		$("#snapReplica").prop("checked", false);
		$("#snapReplica").prop("disabled", true);
		$("#preserveMAC").prop("checked", false);
		$("#preserveMAC").prop("disabled", true);
		$("#enableCache").prop("checked", false);
		$("#enableCache").prop("disabled", true);
	}else{
		$("#use-gfs").prop("disabled", false);
		$("#enableCache").prop("disabled", false);
		$("#preserveMAC").prop("disabled", false);
		$("#snapReplica").prop("disabled", false);
		$("#deleteBackup").prop("disabled", false);
	}
});

async function saveScheduleRep(evt){
        let guy = $(evt.currentTarget);
	let schedID = guy.data("id");
	showLineLoad();
        var args= new Object();

	// replication specific settings
        args["jobType"] = 5;
	let sources =[];
	$('#target-systems li.sched-src-item').each(function() { 
		var guy = new Object();
		guy.uuid = $(this).data('id');
		guy.destUuid = $(this).data('destuuid');
		guy.sr = $(this).data('sr');
		guy.network = $(this).data('network');
		sources.push(guy);
	});
	if(sources.length==0) {
                doToast("No systems defined.  Please add at least 1 system to protect.", "Could not save Job!", "warning");
		finishLineLoad();
		return;
	}
        args["sources"] = sources;
	args["method"] = $("input:radio[name ='processor']:checked").val();
        args["deleteBackup"] = ($("#deleteBackup").prop('checked')) ? 1 : 0;
        args["snapReplica"] = ($("#snapReplica").prop('checked')) ? 1 : 0;
        args["preserveMAC"] = ($("#preserveMAC").prop('checked')) ? 1 : 0;
        args["bootTarget"] = ($("#bootTarget").prop('checked')) ? 1 : 0;
        args["enableCache"] = ($("#enableCache").prop('checked')) ? 1 : 0;
        args["doCBT"] = ($("#cbt").prop('checked')) ? 1 : 0;
        args["QHB"] = ($("#qhb").prop('checked')) ? 1 : 0;

	// shared backup settings (timing, etc)
        args["id"] = schedID;
        args["name"] = $("#schedule-name").val();
        args["policyType"] = 0;
        args["stype"] = $("#sched-freq").val();
        args["hour"] = $("#sched-hour").val();	
	if(args["hour"] > 23){ args["min"] = 23; }
        args["min"] = $("#sched-min").val();	
	if(args["min"] > 59){ args["min"] = 59; }
        args["interval"] = $("#sched-interval").val();
	let days =[];
	$('#weekdayspicker input[type="checkbox"]').each(function() { days.push($(this).prop('checked')); });	
        args["days"] = days;
        args["monthday"] = $("#sched-monthday").val();
	
        args["concurrent"] = $("#numConcurrent").val();
        args["email"] = $("#emailLevel").val();
        if($("#use-gfs").prop('checked')){
                let gfs = $("#gfsProfile").val();
                if(gfs > 0){ args["gfs"] = gfs; }
        }

        let site = $("#sched-a3id").val();

        try{
                var res = await wsPromise("schedule/set", args, site)
		drawSchedulesTable();
        }catch (ex){
                doToast(ex.message, "Failed to Save Schedule!", "error");
        }
        finishLineLoad();
}

//////////////////////////////////////////// Restore Schedule Handling /////////////////////////////
$(document).on('change', '#src-systems-rest', function(){
	$("#src-versions-rest").empty();
	$("#src-versions-rest").append(`<option value='0'>Loading versions, please wait...</option>`);
	$("#src-versions-rest").show();
        let site = $("#sched-a3id").val();
	var args= new Object();
	args["uuid"] = $("#src-systems-rest").val();
	wsCall("versions", args, "populateRestVersions",null, site );
});

$(document).on('click', '.add-rest-src', function(evt){
        var srcVms = $('#src-systems-rest').select2('data');
        var srcVers = $('#src-versions-rest').val();
        let destUuid = $("#dest-uuid").val();
        if(destUuid ==0){
                doToast("Please select your destination location and storage.", "Missing Required Info", "warning");
                return;
        }else if(srcVms.length==0){
                doToast("Please select at least 1 system to restore.", "No systems defined", "warning");
                return;
        }else if(srcVers.length==0){
                doToast("Please select backup version to restore.", "No backup selected", "warning");
                return;
        }
        let destName = $("#dest-uuid option:selected").text().trim().replace(/^>\s*/, '');
        let destSr = $("#dest-sr").val();
        let destSrName = $("#dest-sr option:selected").text().trim();
        let net = $("#dest-network").val();

        let srNote = ` [${destSrName}] `;
        if($("#vhd-row").is(":visible")){ 
                srNote ="[ Default VHD Path ]";
                if($("#dest-vhd").val().length > 0){
                        destSr = $("#dest-vhd").val().trim();
                        srNote = `[ ${destSr} ] `;
                }
        }
        // check if the VM has already been added

        $.each(srcVms, function(i, v){
                if($("#target-systems").find(`[data-id="${v.id}"]`).length > 0){
                        doToast(`System "${v.text}" already defined, skipping duplicated entry.`, "Skipping system", "warning");
                        return;
                }
                let trash = `<i class='fas fa-trash rep-trash clickable float-right' ></i>`;
                let targ = ` <li data-id='${v.id}' data-destuuid='${destUuid}' data-ts=${srcVers} data-sr='${destSr}' data-network='${net}' class='sched-src-item'> 
                           <span><b>${v.text}</b>  [${getDate(srcVers)}]  <i class='fas fa-long-arrow-alt-right'></i> <b>${destName}</b>  ${srNote}  ${trash}</span>
                                </li>
                        `;
                $("#target-systems").append(targ);
        });
        $('#src-systems-rest').val(null).trigger('change');
        $('#dest-uuid').val(0).trigger('change');
        $('#src-versions-rest').empty();
        $('#sr-row').hide();
        $('#vhd-row').hide();
});
$(document).on('click', '.res-trash', function(evt){
        $(this).closest('li').remove();
});

async function saveScheduleRestore(evt){
        let guy = $(evt.currentTarget);
        let schedID = guy.data("id");
        showLineLoad();
        var args= new Object();

        // restore specific settings
        args["jobType"] = 1;
        let sources =[];
        $('#target-systems li.sched-src-item').each(function() {
                var guy = new Object();
                guy.uuid = $(this).data('id');
                guy.destUuid = $(this).data('destuuid');
                guy.sr = $(this).data('sr');
                guy.timestamp = $(this).data('ts');
                guy.network = $(this).data('network');
                sources.push(guy);
        });
        if(sources.length==0) {
                doToast("No systems defined.  Please add at least 1 system to protect.", "Could not save Job!", "warning");
                finishLineLoad();
                return;
        }
        args["sources"] = sources;
        args["bootTarget"] = ($("#bootTarget").prop('checked')) ? 1 : 0;

        // shared backup settings (timing, etc)
        args["id"] = schedID;
        args["min"] = 0;
        args["hour"] = 0;
        args["days"] = [];
        args["interval"] = 0;
        args["name"] = $("#schedule-name").val();
        args["policyType"] = 0;
        args["stype"] = $("#sched-freq").val();

        args["concurrent"] = $("#numConcurrent").val();
        args["email"] = $("#emailLevel").val();

        let site = $("#sched-a3id").val();

        try{
                var res = await wsPromise("schedule/set", args, site)
		if(args["stype"] ==5 && res.Schedule){
			let id = res.Schedule.scheduleID;
			runSchedule(id, site);
		}else{
			drawSchedulesTable();
		}

        }catch (ex){
                doToast(ex.message, "Failed to Save Schedule!", "error");
        }
        finishLineLoad();
}
$(document).on('click', '.copy-guid', function(){
	var id = $(this).data("guid");
	copyToClip(id);
});

