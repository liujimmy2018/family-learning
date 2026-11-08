package cn.family.learning.media

import android.net.Uri
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable fun CameraPage(onPhoto: (Uri)->Unit,onError: (String)->Unit){
    val context=LocalContext.current;val owner=LocalLifecycleOwner.current
    val preview=remember{PreviewView(context)}
    val capture=remember{ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()}
    var provider by remember{mutableStateOf<ProcessCameraProvider?>(null)};var capturing by remember{mutableStateOf(false)}
    DisposableEffect(owner){
        val future=ProcessCameraProvider.getInstance(context)
        var active=true
        future.addListener({if(active)try{provider=future.get();val useCase=Preview.Builder().build().apply{setSurfaceProvider(preview.surfaceProvider)};provider?.unbindAll();provider?.bindToLifecycle(owner,CameraSelector.DEFAULT_BACK_CAMERA,useCase,capture)}catch(_:Exception){onError("无法启动相机，可返回并从相册选图")}},ContextCompat.getMainExecutor(context))
        onDispose{active=false;provider?.unbindAll()}
    }
    Column(Modifier.fillMaxSize()){
        AndroidView(factory={preview},modifier=Modifier.weight(1f).fillMaxWidth())
        Button(onClick={capturing=true;val file=PhotoFiles.newFile(context);capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(),ContextCompat.getMainExecutor(context),object: ImageCapture.OnImageSavedCallback{
            override fun onImageSaved(result: ImageCapture.OutputFileResults){capturing=false;onPhoto(Uri.fromFile(file))}
            override fun onError(error: ImageCaptureException){capturing=false;onError("拍摄失败，请重试")}
        })},enabled=!capturing&&provider!=null,modifier=Modifier.fillMaxWidth().padding(20.dp).heightIn(min=50.dp)){Text(if(capturing)"正在拍摄" else "拍摄并裁剪")}
    }
}
