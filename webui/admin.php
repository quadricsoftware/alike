<?php
$bld = 0;
$bf = "../build.num";
if(file_exists($bf)){
        $bld = trim(file_get_contents($bf));
}

$nm = "/home/alike/configs/node_mode";
if(file_exists($nm)){
        $mode = trim(file_get_contents($nm));
        if($mode == 1){
                showHeadless();
                exit();
        }
}

// just to bust caching for now
//$bld = rand(0, 20000);


session_start();
$force_logout = false;
$dbFile= "/home/alike/Alike/DBs/manager.db";
if(!file_exists($dbFile) && isset($_SESSION['session_token']) && !isset($_REQUEST['login']) ){ 
	$force_logout = true;
}


if(isset($_REQUEST['logout']) || $force_logout){
	$me = $_SERVER['PHP_SELF'];
	session_destroy();
	header("Location: $me");
	exit();
}
if(isset($_REQUEST['login'])){
	include_once('/usr/local/sbin/manager_lib');
	$user = $_REQUEST['username'];
	$pass = $_REQUEST['password'];
	$res = checkAuth($user, $pass);
	if($res->result == "success"){
		$_SESSION["session_token"] = $res->session;
		$_SESSION["email"] = $user;
		$me = $_SERVER['PHP_SELF'];
		header("Location: $me");
	}else{
		showLogin($res->message);
		exit();
	}
}

if (!isset($_SESSION['session_token'])) {
	showLogin();
	exit();
}

function printBgScript(){
?>
	<script>
	function updateBg() {
		const dt = new Date();
		const hour = dt.getHours();
		const guy = document.body;
		guy.className = '';
		let newClass = `sky-bg-${hour < 10 ? '0' : ''}${hour}`;
		guy.classList.add(newClass);
	}

	document.addEventListener("DOMContentLoaded", function () {
		updateBg();
	});

	setInterval(updateBg, 60000);
	</script>
<?php
}

function showLogin($msg=""){
?>
<html>
<head>
<link rel="stylesheet" href="/css/a3.css">  
<link rel="stylesheet" href="/dist/css/bootstrap.min.css"> 
 <link rel="stylesheet" href="/plugins/fontawesome.all.min.css"> 
<style> 
	 body { height:100%;} 
        input[type=text],  
        input[type=password] {  
            width: 100%;  
            padding: 12px 40px;  
            margin: 8px 0;  
            display: inline-block;  
            border: 1px solid #ccc;  
            border-radius: 4px;  
            box-sizing: border-box;  
        }  
          
        button {  
            background-color: #4CAF50;  
            color: white;  
            padding: 14px 20px;  
            margin: 8px 0;  
            border: none;  
            cursor: pointer;  
            width: 100%;  
        }  
        button:hover {  opacity: 0.8;  }  
        .container {  padding: 16px;  }  
        .user { position: relative; } 
        .user i{ 
            position: absolute; 
            left: 25px; 
            top: 25px; 
            color: gray; 
        } 
        .password { position: relative; } 
        .password i{ 
            position: absolute; 
            left: 25px; 
            top: 25px; 
            color: gray; 
        } 
</style>

<?php echo printBgScript(); ?>
    <title>A3 Manager Login</title>
  </head>
  <body >
        <div style="width: 500px;  padding: 25px; border-radius: 8px; background: #f2f2f2; position: absolute; top: 30%; left: 50%; transform: translate(-50%, -30%);">
        <div style="text-align: center;">
                <img style="position:relative; top: -6px;" src="/images/alike-logo.png" > A3<h2>Please Login</h2>
                <p style="font-size: 20px;">
		<form method='post'>
		<div class='row'>
		<input type='hidden' name='login' >
		<div class='user'>
			<input type='text' name='username' placeholder='Local Username' value='alike'>
			<i class="fas fa-user fa-lg"></i> 
		</div>
		<div class='password'>
			<input type='password' name='password' placeholder='Password'>
			<i class="fas fa-key fa-lg"></i> 
		</div>
		<br>
		<input type='submit' value='Login' class='btn btn-block bg-primary bt-lg' style='color: white;'>
		</div>
		<br>
		<?php echo $msg; ?>
                </p>
            </div>
    </div>
<?php
}


function showHeadless(){
        include_once("/usr/local/sbin/shared_lib");
        $mgr = getSetting("managerIP");
	$mnote = "To manage this A3, please login to your <br><a href='https://$mgr/'>A3 Manager</a>";
	if(empty($mgr) || $mgr == "127.0.0.1"){
		$mnote = "No Manager Assigned!<br>Please assign this node via your Alike Manager UI";
	}
?>
<head>
<!-- <link rel="stylesheet" href="/css/alike.css"> -->
<link rel="stylesheet" href="/dist/css/jquery.reject.css">
<link rel="stylesheet" href="/dist/css/bootstrap.min.css">
<style> body { background-image: linear-gradient(to top, #00176D , #0082A2 ); } </style>
<?php echo printBgScript(); ?>
    <title>Headless Node</title>
  </head>
  <body>
        <div style="width: 500px;  padding: 25px; border-radius: 15px; background: #f2f2f2; position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%);">
        <div style="text-align: center;">
                <h2> <img style="position:relative; top: -6px;" src="images/alike-logo.png" > Headless Node</h2><br>
                <p style="font-size: 20px;">
			<?php echo $mnote; ?>
                </p>
            </div>
    </div>
<?php
}

$dark = "false";
if(isset( $_COOKIE['dark-mode'])){
	$dark = $_COOKIE['dark-mode'];
}
$dm = "dark-mode";
if($dark == "false"){ $dm = ""; }

$sb= "";
if(isset($_COOKIE['hide-sidebar'])){
	$sbc = $_COOKIE['hide-sidebar'];
	if($sbc == "true"){ $sb= "sidebar-collapse"; }
}

?>


<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Quadric Software- A3 Manager</title>


<link rel="stylesheet" href="/dist/fonts/gfont.css">
<link rel="stylesheet" href="/plugins/fontawesome.all.min.css">
<link rel="stylesheet" href="/dist/css/adminlte.min.css">
<link rel="stylesheet" href="/css/jquery-confirm.min.css">
<link rel="stylesheet" href="/css/a3.css?v=<?php echo $bld; ?>">
<link rel="stylesheet" href="/css/sumoselect.min.css">
<link rel="stylesheet" href="/plugins/tempusdominus-bootstrap-4.min.css">
<link rel="stylesheet" href="/plugins/select2.min.css">



</head>
<div id='loadingLine'></div>
<body class="hold-transition sidebar-mini <?php echo "$sb $dm"; ?>">
<!-- Site wrapper -->
<div class="wrapper">


  <!-- Navbar navbar-light vs navbar-dar and navbar-<color of choice> -->
  <nav class="main-header navbar navbar-expand navbar-blue navbar-dark border-bottom=0">

    <!-- Left navbar links -->
    <ul class="navbar-nav">
      <li class="nav-item" id='navbar-vis'>
        <a class="nav-link" data-widget="pushmenu" href="#" role="button"><i class="fas fa-bars"></i></a>
      </li>
	<!--
      <li class="nav-item d-none d-sm-inline-block">
        <a href="../../index3.html" class="nav-link">Home</a>
      </li>
      <li class="nav-item d-none d-sm-inline-block">
        <a href="#" class="nav-link">Contact</a>
      </li>
	-->
    </ul>

    <!-- Right navbar links -->
    <ul class="navbar-nav ml-auto">
	<li class="nav-item ">
		<span class="badge badge-warning">Build# <?php echo "$bld"; ?></span>
	</li>

      <!-- Messages Dropdown Menu -->
      <li class="nav-item dropdown">
        <a class="nav-link" data-toggle="dropdown" href="#">
          <i class="far fa-bell fa-lg"></i>
          <span class="badge badge-danger navbar-badge" id="num-alerts-badge">3</span>
        </a>
        <div class="dropdown-menu-wide dropdown-menu dropdown-menu-lg dropdown-menu-right ">
<span id="alert-messages">

</span>
          <div class="dropdown-divider"></div>
          <a href="#" class="dropdown-item dropdown-footer clickable" id="clear-all-alerts">Clear All Alerts</a>
        </div>
      </li>
      <!-- Notifications Dropdown Menu -->
      <li class="nav-item">
        <a class="nav-link" data-widget="fullscreen" href="#" role="button">
          <i class="fas fa-expand-arrows-alt"></i>
        </a>
      </li>
<!--      <li class="nav-item">
        <a class="nav-link" data-widget="control-sidebar" data-slide="true" href="#" role="button">
          <i class="fas fa-th-large"></i>
        </a>
      </li>-->
    </ul>
  </nav>
  <!-- /.navbar -->

  <!-- Main Sidebar Container -->
  <aside class="main-sidebar sidebar-dark-primary elevation-4">
    <!-- Brand Logo -->
    <a href="#" class="brand-link">
      <img src="/images/alike-logo.png" alt="Alike Logo" class="brand-image img-circle elevation-3" style="opacity: .8">
      <span class="brand-text font-weight-light">A3 Manager</span>
    </a>

    <!-- Sidebar -->
    <div class="sidebar">
      <!-- Sidebar user (optional) -->
      <div class="user-panel mt-3 pb-3 mb-3 d-flex">
<!--                  <i class="fas fa-universal-access fa-lg" style="color: #ffffff;"></i> -->
        <div class="info">
          <a href="#" class="d-block"><?php echo $_SESSION['email']; ?></a>
	<a href="?logout=1"><i class="fas fa-sign-out-alt" title='Logout and clear session' data-toggle='tooltip'></i> Sign Out</a>
        </div>
      </div>


      <!-- Sidebar Menu -->
      <nav class="mt-2">
        <ul class="nav nav-pills nav-sidebar flex-column nav-child-indent" data-widget="treeview" role="menu" data-accordion="false">
          <!-- Add icons to the links using the .nav-icon class
               with font-awesome or any other icon font library -->
          <li class="nav-item">
            <a href="#" class="nav-link active clickable go_dash">
              <i class="nav-icon fas fa-tachometer-alt"></i>
              <p>
                Dashboard
              </p>
            </a>
          </li>

          <li class="nav-item">
            <a href="#" class="nav-link nav-category">
              <i class="nav-icon fas fa-chart-pie"></i>
              <p>
                Jobs
                <i class="right fas fa-angle-left"></i>
              </p>
            </a>
            <ul class="nav nav-treeview">
              <li class="nav-item">
                <a href="#" class="nav-link clickable go_jobs-run">
                  <i class="fas fa-running nav-icon"></i>
                  <p>Active Jobs</p>
                </a>
              </li>
              <li class="nav-item">
                <a href="#" class="nav-link clickable go_jobs-hist">
                  <i class="far fa-list-alt nav-icon"></i>
                  <p>Job History</p>
                </a>
              </li>
              <li class="nav-item">
                <a href="#" class="nav-link clickable go_schedules">
                  <i class="far fa-calendar nav-icon"></i>
                  <p>Scheduled Jobs</p>
                </a>
              </li>
              <li class="nav-item">
                <a href="#" class="nav-link clickable go_sched-new">
                  <i class="fas fa-plus-circle nav-icon"></i>
                  <p>New Schedule</p>
                </a>
              </li>
            </ul>
          </li>


          <li class="nav-item">
            <a href="#" class="nav-link nav-category">
              <i class="nav-icon fas fa-edit"></i>
              <p>
                Backups
                <i class="fas fa-angle-left right"></i>
              </p>
            </a>
            <ul class="nav nav-treeview">
              <li class="nav-item">
                <a href="#" class="nav-link clickable go_backup-hist">
                  <i class="far fa-circle nav-icon"></i>
                  <p>Backup History</p>
                </a>
              </li>
              <li class="nav-item">
                <a href="#" class="nav-link clickable go_vault-hist">
                  <i class="far fa-circle nav-icon"></i>
                  <p>Vault History</p>
                </a>
              </li>
              <li class="nav-item">
                <a href="#" class="nav-link go_backup-explorer">
                  <i class="far fa-circle nav-icon "></i>
                  <p>Backup Explorer</p>
                </a>
              </li>

            </ul>
          </li>
          <li class="nav-item">
            <a href="#" class="nav-link nav-category">
              <i class="nav-icon fas fa-server"></i>
              <p>
                Environment
                <i class="fas fa-angle-left right"></i>
              </p>
            </a>
            <ul class="nav nav-treeview">
              <li class="nav-item">
                <a href="#" class="nav-link clickable go_vms">
                  <i class="far fa-circle nav-icon"></i>
                  <p>All Systems</p>
                </a>
              </li>
              <li class="nav-item">
                <a href="#" class="nav-link clickable go_agents">
                  <i class="far fa-circle nav-icon"></i>
                  <p>All Agents</p>
                </a>
              </li>
              <li class="nav-item">
                <a href="#" class="nav-link clickable go_hosts">
                  <i class="far fa-circle nav-icon"></i>
                  <p>Hypervisor Hosts</p>
                </a>
              </li>
              <li class="nav-item">
                <a href="#" class="nav-link clickable go_abds">
                  <i class="far fa-circle nav-icon"></i>
                  <p>ABDs (Xen)</p>
                </a>
              </li>
              <li class="nav-item">
                <a href="#" class="nav-link clickable go_insta">
                  <i class="fas fa-network-wired nav-icon"></i>
                  <p>InstaBoots (Xen)</p>
                </a>
              </li>
            </ul>
          </li>



          <li class="nav-item">
            <a href="#" class="nav-link nav-category">
              <i class="nav-icon fas fa-table"></i>
              <p>
                Configuration
                <i class="fas fa-angle-left right"></i>
              </p>
            </a>
            <ul class="nav nav-treeview">
              <li class="nav-item">
                <a href="#" class="nav-link clickable go_a3s">
                  <i class="fas fa-box nav-icon"></i>
                  <p>Manage A3s</p>
                </a>
              </li>
              <li class="nav-item">
                <a href="#" class="nav-link clickable go_settings">
                  <i class="fas fa-cogs nav-icon"></i>
                  <p>System Settings</p>
                </a>
              </li>
              <li class="nav-item">
                <a href="#" class="nav-link clickable go_help">
                  <i class="fas fa-life-ring nav-icon"></i>
                  <p>Get Support</p>
                </a>
              </li>

            </ul>
          </li>
        </ul>
      </nav>
      <!-- /.sidebar-menu -->
    </div>
    <!-- /.sidebar -->
  </aside>

  <!-- Content Wrapper. Contains page content -->
  <div class="content-wrapper">
    <!-- Content Header (Page header) -->
    <section class="content-header">
      <div class="container-fluid">
        <div class="row d-flex justify-content-between">
          <div class="p-6">
            <h1 id='page-header'>Header</h1>
          </div>
          <div class="p-6" id='header-controls'> </div>
        </div>
      </div><!-- /.container-fluid -->
    </section>

    <!-- Main content -->
    <section class="content">
      <div class="container-fluid">
        <div class="row">
          <div class="col-12">
            <!-- Default box -->
            <div class="card">
              <div class="card-header">
                <h3 class="card-title" id='page-table-header'>Main content title</h3>

                <div class="card-tools">
<!--                  <button type="button" class="btn btn-tool" data-card-widget="collapse" title="Collapse">
                    <i class="fas fa-minus"></i>
                  </button>
                  <button type="button" class="btn btn-tool" data-card-widget="remove" title="Remove">
                    <i class="fas fa-times"></i>
                  </button> -->
                  <div class="input-group input-group-sm" style="width: 150px;" id="top-search">
                    <input type="text" name="table_search" class="form-control float-right" placeholder="Search">
                    <div class="input-group-append">
                      <button type="submit" class="btn btn-default"> <i class="fas fa-search"></i> </button>
		    </div>
		  </div>
		
                </div>
              </div>
              <div class="card-body table-responsive p-0" id='dataTableDiv'>
              </div>
              <!-- /.card-body -->
              <div class="card-footer" id="page-footer">
              </div>
              <!-- /.card-footer-->
            </div>
            <!-- /.card -->
          </div>
        </div>
      </div>
    </section>
    <!-- /.content -->
  </div>
  <!-- /.content-wrapper -->

  <footer class="main-footer">
    <div class="float-right d-none d-sm-block">
	<span id='theme-switch'>
		<i class="far fa-sun fa-lg" style='color:#ebd234;'></i> &nbsp;
		<i class="far fa-moon fa-lg"></i>	
	</span>
    </div>
    <strong>Copyright &copy; 2009-<?php echo date('Y'); ?> <a href="https://www.alikebackup.com" target="_new">Quadric Software, Inc.</a></strong> All rights reserved.
  </footer>

  <!-- Control Sidebar -->
  <aside class="control-sidebar control-sidebar-dark">
    <!-- Control sidebar content goes here -->
  </aside>
  <!-- /.control-sidebar -->
</div>
<!-- ./wrapper -->


<!-- Modal for all the things -->
<div class="modal fade" id="modal-main" data-backdrop="static" tabindex="-1" role="dialog" aria-hidden="true">
  <div class="modal-dialog " role="document">
    <div class="modal-content">
      <div class="modal-header">
        <h5 class="modal-title" id="main-modal-title"></h5>
        <button type="button" class="close" data-dismiss="modal" aria-label="Close">
          <span aria-hidden="true">&times;</span>
        </button>
      </div>
      <div class="modal-body" id="modalBody">
        ...
      </div>
      <div class="modal-footer">
        <button type="button" class="btn btn-secondary" data-dismiss="modal">Close</button>
        <button type="button" class="btn btn-primary" id='modal-ok-button'>OK</button>
      </div>
    </div>
  </div>
</div>


<script src="/plugins/jquery.min.js"></script>
<script src="/js/jquery-ui.min.js"></script>
<script src="/js/jquery.mask.min.js"></script>
<script type='text/javascript' src='/js/Chart.min.js?v=4.4.1'></script> 
<script type="text/javascript" src="/js/jquery.confirm.min.js"></script>
<script type="text/javascript" src="/js/jquery.sumoselect.min.js"></script>
<script type='text/javascript' src='/js/utils.js?v=<?php echo $bld; ?>'></script>
<script type='text/javascript' src='/js/a3.js?v=<?php echo $bld; ?>'></script>
<script type='text/javascript' src='/js/events.js?v=<?php echo $bld; ?>'></script>
<script type='text/javascript' src='/js/objects.js?v=<?php echo $bld; ?>'></script>
<script src="/plugins/popper.umd.min.js"></script>
<script src="/plugins/bootstrap.min.js"></script>
<script src="/plugins/bootstrap.bundle.min.js"></script>
<script src="/dist/js/adminlte.min.js"></script>
<script src="/plugins/jquery.inputmask.min.js"></script>
<script src="/plugins/jquery.dataTables.min.js"></script>
<script src="/plugins/dataTables.bootstrap4.min.js"></script>
<script src="/plugins/dataTables.responsive.min.js"></script>
<script src="/plugins/responsive.bootstrap4.min.js"></script>
<script src="/plugins/dataTables.buttons.min.js"></script>
<script src="/plugins/buttons.bootstrap4.min.js"></script>
<script src="/plugins/jquery.knob.min.js"></script>
<script src="/plugins/jquery.sparkline.min.js"></script>
<script src="/plugins/moment.min.js"></script>
<script src="/plugins/tempusdominus-bootstrap-4.min.js"></script>
<script src="/plugins/jszip.min.js"></script>
<script src="/plugins/pdfmake.min.js"></script>
<script src="/plugins/vfs_fonts.js"></script>
<script src="/plugins/buttons.html5.min.js"></script>
<script src="/plugins/buttons.print.min.js"></script>
<script src="/plugins/buttons.colVis.min.js"></script>
<script src="/plugins/select2.full.min.js"></script>

        <script>
            checkCurrentSession();
        </script>

</body>
</html>
