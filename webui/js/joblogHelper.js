function checkForKnownMessage(jle){
    var tip="";
    if(jle.description.indexOf("Failed to connect due to network or firewall error") != -1){
        tip = "<a href='"+getText('qhbKB')+"' target=\"_new\"><img src=\"/images/kb_tip.png\" title='"+getText('relatedKB')+"' border=0></a> &nbsp;";
    }
    else if(jle.description.indexOf("VSS snapshot is no longer available") != -1){
        tip = "<a href='"+getText('vssMissingKB')+"' target='_new'><img src='/images/kb_tip.png' title='"+getText('relatedKB')+"' border=0></a> &nbsp;";
    }
    else if(jle.description.indexOf("VSS snapshot was deleted") != -1){
        tip = "<a href='"+getText('vssMissingKB')+"' target='_new'><img src='/images/kb_tip.png' title='"+getText('relatedKB')+"' border=0></a> &nbsp;";
    }
    else if(jle.description.indexOf("Replicating") != -1 && jle.description.indexOf("(ID ") != -1){
        var pos = jle.description.indexOf("(ID");
        var end = jle.description.lastIndexOf(")");
        var id = jle.description.substring(pos+4,end);
        var vm = jle.description.indexOf(":");
        var newmsg = jle.description.substring(vm+2,pos);
        var newmsg2 = jle.description.substring(end+1);
	var vst = jle.description.indexOf("Replicating ");
	var verb = jle.description.substring(vst,vm);
        jle.description = verb+": <a href=\"javascript:viewVmDetails("+id+", true);\"><img title='View System Details' style='height:12px; width:12px' src='/images/vm-details.png'></a> <a title='Jump To System' class='clickable' href=\"javascript:jumpToAnchor('vid_"+id+"','detailModal');\">"+newmsg+"</a> " +newmsg2;
    }
    else if(jle.description.indexOf("Restoring: ") != -1 && jle.description.indexOf("(ID ") != -1){
        var pos = jle.description.indexOf("(ID");
        var end = jle.description.lastIndexOf(")");
        var id = jle.description.substring(pos+4,end);
        var vm = jle.description.indexOf(":");
        var newmsg = jle.description.substring(vm+2,pos);
        var newmsg2 = jle.description.substring(end+1);
        jle.description = "Restoring: <a href=\"javascript:viewVmDetails("+id+", true);\"><img title='View System Details' style='height:12px; width:12px' src='/images/vm-details.png'></a> <a title='Jump To System' class='clickable' href=\"javascript:jumpToAnchor('vid_"+id+"','detailModal');\">"+newmsg+"</a> " +newmsg2;
    }
	else if(jle.description.indexOf("Vaulting: ") != -1 && jle.description.indexOf("(ID ") != -1){
        var pos = jle.description.indexOf("(ID");
        var end = jle.description.lastIndexOf(")");
        var id = jle.description.substring(pos+4,end);
        var vm = jle.description.indexOf(":");
        var newmsg = jle.description.substring(vm+2,pos);
        var newmsg2 = jle.description.substring(end+1);
        jle.description = "Vaulting: <a href=\"javascript:viewVmDetails("+id+", true);\"><img title='View System Details' style='height:12px; width:12px' src='/images/vm-details.png'></a> <a title='Jump To System' class='clickable' href=\"javascript:jumpToAnchor('vid_"+id+"','detailModal');\">"+newmsg+"</a> " +newmsg2;
    }

    else if(jle.description.indexOf("Processing: ") != -1 && jle.description.indexOf("(ID ") != -1){
        var pos = jle.description.indexOf("(ID");
        var end = jle.description.lastIndexOf(")");
        var id = jle.description.substring(pos+4,end);
        var vm = jle.description.indexOf(":");
        var newmsg = jle.description.substring(vm+2,pos);
        var newmsg2 = jle.description.substring(end+1);
        jle.description = "Processing: <a href=\"javascript:viewVmDetails("+id+", true);\"><img title='View System Details' style='height:12px; width:12px' src='/images/vm-details.png'></a> <a title='Jump To System' class='clickable' href=\"javascript:jumpToAnchor('vid_"+id+"','detailModal');\">"+newmsg+"</a> " +newmsg2;
    }
    else if(jle.description.indexOf("Alike ") != -1 && jle.description.indexOf("build ") != -1){
        var pos = jle.description.indexOf("Alike");
        var build = jle.description.substring(pos,jle.description.length);
        if(build != window.alikeBuild){
            jle.description = "<b>"+ jle.description +"</b>";
        }
    }    
    else if(jle.description.indexOf("Restore failed, Timed out waiting for version to commit") != -1){
        tip = "<a href=\""+getText('dedupKB')+"\" target=\"_new\"><img src=\"/images/kb_tip.png\" title='"+getText('relatedKB')+"' border=0></a> &nbsp;";
    }
    else if(jle.description.indexOf("No ABD Template found for this XenPool") != -1){
        var pos = jle.description.indexOf("No ABD");
        var tmp = jle.description.substring(0,pos);
        jle.description = tmp +'<a href="javascript:listABDs();"><b>'+jle.description.substring(pos)+"</b></a>";
        tip = "<a href='"+getText('abdTsKB')+"' target='_new'><img src='/images/kb_tip.png' title='"+getText('relatedKB')+"' border=0></a> &nbsp;";
    }
    else if(jle.description.indexOf("ABD Template could no longer be found for this pool") != -1){
        var pos = jle.description.indexOf("Please");
        var tmp = jle.description.substring(0,pos);
        jle.description = tmp +'<a href="javascript:listABDs();"><b>'+jle.description.substring(pos)+"</b></a>";
        tip = "<a href='"+getText('abdTsKB')+"' target='_new'><img src='/images/kb_tip.png' title='"+getText('relatedKB')+"' border=0></a> &nbsp;";
    }
    else if(jle.description.indexOf("IP addresses have been assigned to this pool") != -1){
        var pos = jle.description.indexOf("No IP");
        var tmp = jle.description.substring(0,pos);
        jle.description = tmp +'<a href="javascript:listABDs();"><b>'+jle.description.substring(pos)+"</b></a>";
        tip = "<a href='"+getText('abdTsKB')+"' target='_new'><img src='/images/kb_tip.png' title='"+getText('relatedKB')+"' border=0></a> &nbsp;";
    }
    else if(jle.description.indexOf("No IP Addresses available for ABD") != -1){
        var pos = jle.description.indexOf("Please");
        var tmp = jle.description.substring(0,pos);
        jle.description = tmp +'<a href="javascript:listABDs();"><b>'+jle.description.substring(pos)+"</b></a>";
        tip = "<a href='"+getText('abdTsKB')+"' target='_new'><img src='/images/kb_tip.png' title='"+getText('relatedKB')+"' border=0></a> &nbsp;";
    }
    else if(jle.description.indexOf("Waited, but all ABD IP addresses are currently in use") != -1){
        var pos = jle.description.indexOf("Waited");
        var tmp = jle.description.substring(0,pos);
        jle.description = tmp +'<a href="javascript:listABDs();"><b>'+jle.description.substring(pos)+"</b></a>";
        tip = "<a href='"+getText('abdTsKB')+"' target='_new'><img src='/images/kb_tip.png' title='"+getText('relatedKB')+"' border=0></a> &nbsp;";
    }
    else if(jle.description.indexOf("ABD Failed to mount DataStore. mount: Network is unreachable") != -1){
        tip = "<a href='"+getText('abdTsKB')+" target=\"_new\"><img src=\"/images/kb_tip.png\" title='"+getText('relatedKB')+"' border=0></a> &nbsp;";
    }
    else if(jle.description.indexOf("Q-Hybrid Backup unavailable: Failed to connect due to network or firewall error") != -1){
        tip = "<a href='"+getText('qhbKB')+"' target=\"_new\"><img src=\"/images/kb_tip.png\" title='"+getText('relatedKB')+"' border=0></a> &nbsp;";
    }
    else if(jle.description.indexOf("Unable to find VM") != -1){
        tip = "<a href='"+getText('noVMKB')+"' target=\"_new\"><img src=\"/images/kb_tip.png\" title='"+getText('relatedKB')+"' border=0></a> &nbsp;";
    }
    else if(jle.description.indexOf("is running on an unlicensed host") != -1){
        tip = "<a href='"+getText('noVMKB')+"' target=\"_new\"><img src=\"/images/kb_tip.png\" title='"+getText('relatedKB')+"' border=0></a> &nbsp;";
    }
    else if(jle.description.indexOf("The specified storage repository has insufficient space") != -1){
        tip = "<a href='"+getText('noSRSpaceKB')+"' target=\"_new\"><img src='/images/kb_tip.png' title='"+getText('relatedKB')+"' border=0></a> &nbsp;";
    }
    else if(jle.description.indexOf("Globally deduplicating and committing") != -1){
        tip = "<a href='"+getText('globaldedupKB')+"' target=\"_new\"><img src='/images/kb_tip.png' title='"+getText('relatedKB')+"' border=0></a> &nbsp;";
    }
    else if(jle.description.indexOf("has too many snapshots") != -1){
        tip = "<a href='"+getText('longChainKB')+"' target=\"_new\"><img src='/images/kb_tip.png' title='"+getText('relatedKB')+"' border=0></a> &nbsp;";
    }else if(jle.description.indexOf("has too many snapshots") != -1){
        tip = "<a href='"+getText('longChainKB')+"' target=\"_new\"><img src='/images/kb_tip.png' title='"+getText('relatedKB')+"' border=0></a> &nbsp;";
    }else if(jle.description.indexOf("IRPStackSize is too small") != -1){
        var pos = jle.description.indexOf("IRP");
        var tmp = jle.description.substring(pos);
        tip = "<a href='"+getText('irpKbUrl')+"' target='_new'><img src='/images/kb_tip.png' title='"+getText('relatedKB')+"' border=0></a> &nbsp;";
        jle.description = tmp +'<a href="'+getText('irpKbUrl')+'b>'+jle.description.substring(pos)+"</b></a> ";
    }else if(jle.description.indexOf("No Credential Profiles") != -1){
        var pos = jle.description.indexOf("Please");
        var tmp = jle.description.substring(0, pos);
        jle.description = tmp +'<a href="#" class="openAP closeDetailModal"><b>'+jle.description.substring(pos)+"</b></a>";
        tip = "<a href='"+getText('apKbUrl')+"' target='_new'><img src='/images/kb_tip.png' title='"+getText('relatedKB')+"' border=0></a> &nbsp;";
    } 
    
    return tip;
}

function checkVMProgress(e){    
	 // status (0 = success, 1=failed, 2=in progress,3=not started, 4= unknown)
    //0=complete, 1=failed, 2=?
    if(e.description.indexOf("Backup complete") != -1){
        return 0;
    }
    else if(e.description.indexOf("of VM") != -1 && e.description.indexOf("is complete") != -1){
        return 0;
    }
    else if(e.description.indexOf("Replicated VM:") != -1){
        return 0;
    }else if(e.description.indexOf("Replication completed for VM:") != -1){
        return 0;
    }
    else if(e.description.indexOf("of VM") != -1 && e.description.indexOf("was not completed") != -1){
        return 1;
    }
    else if(e.description.indexOf("Restore failed") != -1){
        return 1;
    }else if(e.description.indexOf("Backup failed") != -1){
        return 1;
    }else if(e.description.indexOf("Error processing VM") != -1){
        return 1;
    }else if(e.description.indexOf("Beginning work on") != -1){
        return 2;
    }else if(e.description.indexOf("for cachemap") != -1){
        return 3;
    }else if(e.description.indexOf("Detected initial backup of system") != -1){
        return 3;
    }
    return 4;	// no comment
}
