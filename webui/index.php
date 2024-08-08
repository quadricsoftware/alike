<html>

<?php
	$bld = 0;
	$bf = "../build.num";
	if(file_exists($bf)){
		$bld = trim(file_get_contents($bf));
	}
?>

<meta http-equiv='Content-Type' content='text/html; charset=utf-8'>
<meta http-equiv="pragma" content="no-cache" />
<title>Alike Manager</title>
<head>
<link rel="stylesheet" href="/css/alike.css?v=<?php echo $bld; ?>">
<link rel="stylesheet" href="/css/jquery.reject.css">
<link rel="stylesheet" href="/css/bootstrap.min.css">
<link rel="stylesheet" href="/css/bootstrap-theme.min.css">
<script src="/js/jquery.min-1.11.2.js"></script>
<script src="/js/jquery.reject.min.js"></script>
<script src="/js/bootstrap.min.js"></script>
<!--
<script type="text/javascript"> 
$(function() {
    $.reject({
         reject: {
             safari: false, // Apple Safari
             chrome: false, // Google Chrome
             firefox:false, firefox1: true, firefox2: true , // Mozilla Firefox
             msie: 10, // Microsoft Internet Explorer
             opera: true, // Opera
             konqueror: true, // Konqueror (Linux)
             unknown: true // Everything else
         },
        header: 'Please use a supported browser to continue', // Header Text  
        paragraph1: 'The Alike manager requires a modern web browser to function', // Paragraph 1  
        paragraph2: 'Please use or install one of the following browsers below to proceed', 
        close: true,
        display: ['chrome','firefox','safari']        
     }); //
    return false;
});
</script>
-->
<script>window.location.href="/admin.php";</script>

