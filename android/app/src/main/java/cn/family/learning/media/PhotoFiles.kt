package cn.family.learning.media

import android.content.Context
import android.graphics.*
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

object PhotoFiles {
    suspend fun import(context: Context,uri: Uri): File=withContext(Dispatchers.IO){
        val resolver=context.contentResolver
        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
        resolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,bounds)}
        require(bounds.outWidth>0&&bounds.outHeight>0){"无法读取这张图片"}
        var sample=1;while(bounds.outWidth/sample>4096||bounds.outHeight/sample>4096)sample*=2
        val bitmap=resolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{inSampleSize=sample})}?:error("无法解码图片")
        val orientation=resolver.openInputStream(uri)?.use{ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)}?:ExifInterface.ORIENTATION_NORMAL
        val matrix=Matrix().apply {when(orientation){
            ExifInterface.ORIENTATION_ROTATE_90->postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180->postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270->postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL->postScale(-1f,1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL->postScale(1f,-1f)
            ExifInterface.ORIENTATION_TRANSPOSE->{postRotate(90f);postScale(-1f,1f)}
            ExifInterface.ORIENTATION_TRANSVERSE->{postRotate(270f);postScale(-1f,1f)}
        }}
        val oriented=if(matrix.isIdentity)bitmap else Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true)
        val file=newFile(context);file.outputStream().use{oriented.compress(Bitmap.CompressFormat.JPEG,92,it)}
        if(oriented!==bitmap)oriented.recycle();bitmap.recycle();file
    }
    fun newFile(context: Context): File {val dir=File(context.filesDir,"imports").apply{mkdirs()};return File(dir,UUID.randomUUID().toString()+".jpg")}
    suspend fun crop(context: Context,bitmap: Bitmap,left: Float,top: Float,right: Float,bottom: Float): File=withContext(Dispatchers.IO){
        val x=(left*bitmap.width).toInt().coerceIn(0,bitmap.width-1);val y=(top*bitmap.height).toInt().coerceIn(0,bitmap.height-1)
        val width=((right-left)*bitmap.width).toInt().coerceIn(1,bitmap.width-x);val height=((bottom-top)*bitmap.height).toInt().coerceIn(1,bitmap.height-y)
        require(width>=20&&height>=20){"裁剪区域太小，请重新选择"}
        val clipped=Bitmap.createBitmap(bitmap,x,y,width,height);val file=newFile(context)
        file.outputStream().use{clipped.compress(Bitmap.CompressFormat.JPEG,92,it)};if(clipped!==bitmap)clipped.recycle();file
    }
}
