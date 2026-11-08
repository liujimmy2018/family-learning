package cn.family.learning.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlin.math.*

@Composable fun CropPage(path: String,onDone: (String)->Unit,onError: (String)->Unit){
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var bitmap by remember(path){mutableStateOf<Bitmap?>(null)};var crop by remember(path){mutableStateOf(Rect(0.02f,0.02f,0.98f,0.98f))};var saving by remember{mutableStateOf(false)}
    LaunchedEffect(path){bitmap=withContext(Dispatchers.IO){BitmapFactory.decodeFile(path)}}
    val image=bitmap
    DisposableEffect(image){onDispose{if(image!=null&&!image.isRecycled)image.recycle()}}
    Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        Text("拖动四个角调整区域，拖动框内可移动。只保留需要学习的内容。")
        if(image!=null){
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){
                val ratio=image.width.toFloat()/image.height
                val width=minOf(maxWidth,maxHeight*ratio)
                Canvas(Modifier.width(width).aspectRatio(ratio).pointerInput(image){
                    var handle=0
                    detectDragGestures(onDragStart={point->
                        val corners=listOf(Offset(crop.left*size.width,crop.top*size.height),Offset(crop.right*size.width,crop.top*size.height),Offset(crop.right*size.width,crop.bottom*size.height),Offset(crop.left*size.width,crop.bottom*size.height))
                        val nearest=corners.indices.minBy{(corners[it]-point).getDistance()}
                        handle=if((corners[nearest]-point).getDistance()<64.dp.toPx())nearest+1 else 0
                    }){change,delta->
                        change.consume();val dx=delta.x/size.width;val dy=delta.y/size.height
                        crop=when(handle){
                            1->Rect((crop.left+dx).coerceIn(0f,crop.right-0.05f),(crop.top+dy).coerceIn(0f,crop.bottom-0.05f),crop.right,crop.bottom)
                            2->Rect(crop.left,(crop.top+dy).coerceIn(0f,crop.bottom-0.05f),(crop.right+dx).coerceIn(crop.left+0.05f,1f),crop.bottom)
                            3->Rect(crop.left,crop.top,(crop.right+dx).coerceIn(crop.left+0.05f,1f),(crop.bottom+dy).coerceIn(crop.top+0.05f,1f))
                            4->Rect((crop.left+dx).coerceIn(0f,crop.right-0.05f),crop.top,crop.right,(crop.bottom+dy).coerceIn(crop.top+0.05f,1f))
                            else->{val x=dx.coerceIn(-crop.left,1f-crop.right);val y=dy.coerceIn(-crop.top,1f-crop.bottom);Rect(crop.left+x,crop.top+y,crop.right+x,crop.bottom+y)}
                        }
                    }
                }){
                    drawImage(image.asImageBitmap(),dstSize=androidx.compose.ui.unit.IntSize(size.width.toInt(),size.height.toInt()))
                    val l=crop.left*size.width;val t=crop.top*size.height;val r=crop.right*size.width;val b=crop.bottom*size.height
                    val shade=Color.Black.copy(alpha=0.45f)
                    drawRect(shade,size=androidx.compose.ui.geometry.Size(size.width,t));drawRect(shade,topLeft=Offset(0f,b),size=androidx.compose.ui.geometry.Size(size.width,size.height-b))
                    drawRect(shade,topLeft=Offset(0f,t),size=androidx.compose.ui.geometry.Size(l,b-t));drawRect(shade,topLeft=Offset(r,t),size=androidx.compose.ui.geometry.Size(size.width-r,b-t))
                    drawRect(Color.White,topLeft=Offset(l,t),size=androidx.compose.ui.geometry.Size(r-l,b-t),style=Stroke(2.dp.toPx()))
                    listOf(Offset(l,t),Offset(r,t),Offset(r,b),Offset(l,b)).forEach{drawCircle(Color.White,7.dp.toPx(),it)}
                }
            }
            OutlinedButton(onClick={bitmap=Bitmap.createBitmap(image,0,0,image.width,image.height,Matrix().apply{postRotate(90f)},true);crop=Rect(0.02f,0.02f,0.98f,0.98f)},enabled=!saving,modifier=Modifier.fillMaxWidth()){Text("顺时针旋转90度")}
            Button(onClick={saving=true;scope.launch{try{val file=PhotoFiles.crop(context,image,crop.left,crop.top,crop.right,crop.bottom);onDone(file.path)}catch(e:Exception){onError(e.message?:"裁剪失败")}finally{saving=false}}},enabled=!saving,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp)){Text("使用此区域")}
        }else CircularProgressIndicator()
    }
}
