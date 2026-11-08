package cn.family.learning.ai;

import cn.family.learning.common.Values;

import cn.family.learning.common.*;
import cn.family.learning.data.Rows.Job;
import cn.family.learning.learning.*;
import cn.family.learning.learning.Dtos.SpeechInput;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.*;
import javax.annotation.PreDestroy;

@Component
public class JobWorker {
    private final JobService jobs;private final AssetService assets;private final DashScopeProvider ai;private final String role;
    private final ScheduledExecutorService renewals=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"job-lease-renewal");t.setDaemon(true);return t;});
    public JobWorker(JobService jobs,AssetService assets,DashScopeProvider ai,@Value("${app.role}")String role){this.jobs=jobs;this.assets=assets;this.ai=ai;this.role=role;}
    @Scheduled(fixedDelayString="${app.worker-poll-ms}") public void tick(){if(!role.equals("worker")&&!role.equals("all"))return;runOne();}
    public boolean runOne(){
        Job j=jobs.claim();if(j==null)return false;
        ScheduledFuture<?> renewal=renewals.scheduleAtFixedRate(()->{try{jobs.renew(j);}catch(RuntimeException ignored){}},30,30,TimeUnit.SECONDS);
        try{
            if("RECOGNITION".equals(j.type)){
                JobService.RecognitionInput input=Json.read(j.inputJson,JobService.RecognitionInput.class);List<DashScopeProvider.Picture> pictures=new ArrayList<>();
                for(String id:input.assetIds()){cn.family.learning.data.Rows.Asset a=assets.asset(j.ownerId,j.childId,LearningService.requireId(id));pictures.add(new DashScopeProvider.Picture(a.contentType,assets.content(j.ownerId,j.childId,a.id)));}
                jobs.success(j,Values.map("words",ai.recognize(pictures,input.type(),input.grade())));
            }else if("WORD_LOOKUP".equals(j.type)){
                JobService.WordLookupInput input=Json.read(j.inputJson,JobService.WordLookupInput.class);jobs.success(j,Values.map("word",ai.lookup(input.text(),input.grade())));
            }else if("SPEECH".equals(j.type)){
                SpeechInput r=Json.read(j.inputJson,SpeechInput.class);jobs.audioSuccess(j,ai.synthesize(r.text(),r.language()));
            }else jobs.failure(j,"TASK_INVALID","任务类型不支持",false);
        }catch(ApiException e){jobs.failure(j,e.code,e.getMessage(),e.status==429||e.code.equals("AI_UNAVAILABLE"));}
        catch(InterruptedException e){Thread.currentThread().interrupt();jobs.failure(j,"TASK_INTERRUPTED","任务被中断，请重试",true);}
        catch(Exception e){jobs.failure(j,"PROCESSING_FAILED","处理失败，请重试或检查服务配置",true);}
        finally{renewal.cancel(false);}
        return true;
    }
    @PreDestroy public void close(){renewals.shutdownNow();}
}
