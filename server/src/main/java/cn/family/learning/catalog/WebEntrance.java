package cn.family.learning.catalog;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
@Controller public class WebEntrance implements WebMvcConfigurer {
    @GetMapping({"/","/web","/web/"}) public String home(){return "redirect:/web/index.html";}
    @Override public void addResourceHandlers(ResourceHandlerRegistry registry){registry.addResourceHandler("/web/**").addResourceLocations("classpath:/static/web/").setCachePeriod(0);}
}
