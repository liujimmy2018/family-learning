package cn.family.learning.catalog;

import cn.family.learning.common.*;
import cn.family.learning.catalog.CatalogDtos.Entry;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.util.*;
import java.util.zip.*;

@Component
public class ExcelCatalog {
    private static final String[] HEADERS={"年级","上下册","所属单元","中文","英文","音标"};
    private static final String[] CHINESE_HEADERS={"年级","上下册","所属单元","生字词","拼音"};
    public static final int MAX_ROWS=5000;
    public Map<String,Object> preview(MultipartFile file)throws IOException {
        return preview(file,false);
    }
    public Map<String,Object> preview(MultipartFile file,boolean chinese)throws IOException {
        String[] headers=chinese?CHINESE_HEADERS:HEADERS;
        if(file.isEmpty()||file.getSize()>8*1024*1024)throw ApiException.invalid("请导入不超过8MB的xlsx文件");
        String name=file.getOriginalFilename();
        if(name==null||!name.toLowerCase(Locale.ROOT).endsWith(".xlsx"))throw ApiException.invalid("仅支持xlsx，请先下载模板填写");
        byte[] bytes=file.getBytes();checkZip(bytes);
        ZipSecureFile.setMaxEntrySize(32L*1024*1024);
        List<Entry> entries=new ArrayList<>();List<Map<String,Object>> errors=new ArrayList<>();Set<String> seen=new HashSet<>();
        try(XSSFWorkbook book=new XSSFWorkbook(new ByteArrayInputStream(bytes))){
            if(book.getNumberOfSheets()==0)throw ApiException.invalid("Excel中没有工作表");
            Sheet sheet=book.getSheetAt(0);DataFormatter format=new DataFormatter(Locale.CHINA);
            for(int col=0;col<headers.length;col++)
                if(!headers[col].equals(cell(sheet.getRow(0),col,format)))
                    throw ApiException.invalid("第一行需依次为："+String.join("、",headers));
            if(sheet.getLastRowNum()>MAX_ROWS)throw ApiException.invalid("每次最多导入5000行，请分批整理");
            for(int n=1;n<=sheet.getLastRowNum();n++){
                Row row=sheet.getRow(n);if(row==null)continue;
                try{
                    List<String> values=new ArrayList<>();for(int c=0;c<headers.length;c++)values.add(cell(row,c,format));
                    if(values.stream().allMatch(String::isEmpty))continue;
                    String grade=values.get(0).replace("年级","");if(!grade.matches("[1-6]"))throw ApiException.invalid("年级需为1—6");
                    Entry e=new Entry();
                    e.grade=Integer.parseInt(grade);
                    e.semester=values.get(1);
                    e.unit=values.get(2);
                    e.english=values.get(chinese?3:4);
                    e.chinese=values.get(3);
                    e.phonetic=values.get(chinese?4:5);
                    e.ordinal=entries.size();
                    CatalogRules.entry(e,chinese);
                    String key=e.grade+"|"+e.semester+"|"+e.unit+"|"+CatalogRules.wordKey(e.english);
                    if(!seen.add(key))throw ApiException.invalid(chinese?"同一年级、上下册及单元内生字词重复":"同一年级、上下册及单元内英文重复");
                    entries.add(e);
                }catch(ApiException e){errors.add(Values.map("row",n+1,"message",e.getMessage()));}
            }
        }catch(ApiException e){throw e;}catch(Exception e){throw ApiException.invalid("无法读取Excel，请使用未加密的标准xlsx模板，且不要使用公式");}
        if(entries.isEmpty()&&errors.isEmpty())throw ApiException.invalid("词表中没有单词，请填写后重新导入");
        return Values.map("entries",entries,"errors",errors,"valid",errors.isEmpty(),"count",entries.size());
    }
    private static String cell(Row row,int col,DataFormatter format){
        if(row==null)return "";Cell c=row.getCell(col);if(c==null)return "";
        if(c.getCellType()==CellType.FORMULA||c.getCellType()==CellType.ERROR)throw ApiException.invalid("不支持公式或错误单元格，请粘贴为纯文本");
        return format.formatCellValue(c).trim();
    }
    static void checkZip(byte[] bytes)throws IOException {
        Set<String> names=new HashSet<>();long total=0;int count=0;boolean content=false;
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes))){ZipEntry e;byte[] buffer=new byte[8192];
            while((e=zip.getNextEntry())!=null){
                if(++count>500||!names.add(e.getName())||e.getName().contains("..")||e.getName().endsWith("vbaProject.bin"))throw ApiException.invalid("Excel文件结构不支持，请重新保存为xlsx");
                if("[Content_Types].xml".equals(e.getName()))content=true;
                int n;while((n=zip.read(buffer))!=-1){total+=n;if(total>32L*1024*1024)throw ApiException.invalid("Excel解压内容过大，请简化文件后重试");}
            }
        }catch(ZipException e){throw ApiException.invalid("文件不是有效的xlsx");}
        if(!content)throw ApiException.invalid("文件不是有效的xlsx");
    }
    public byte[] template()throws IOException {
        return template(false);
    }
    public byte[] template(boolean chinese)throws IOException {
        String[] headers=chinese?CHINESE_HEADERS:HEADERS;
        try(XSSFWorkbook book=new XSSFWorkbook();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            Sheet sheet=book.createSheet("词表");sheet.createFreezePane(0,1);
            CellStyle header=book.createCellStyle();header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font font=book.createFont();font.setColor(IndexedColors.WHITE.getIndex());font.setBold(true);header.setFont(font);
            Row top=sheet.createRow(0);top.setHeightInPoints(26);for(int c=0;c<headers.length;c++){Cell cell=top.createCell(c);cell.setCellValue(headers[c]);cell.setCellStyle(header);sheet.setColumnWidth(c,new int[]{12,12,20,32,28,28}[c]*256);}
            String[][] examples=chinese
                ?new String[][]{{"1","下册","第一单元","春","chūn"},{"1","下册","第一单元","春天","chūn tiān"},{"1","下册","第二单元","阳光","yáng guāng"}}
                :new String[][]{{"3","上册","Unit 1","苹果","apple","/ˈæpəl/"},{"3","上册","Unit 1","香蕉","banana","/bəˈnɑːnə/"},{"3","上册","Unit 2","书","book","/bʊk/"}};
            for(int r=0;r<examples.length;r++){Row row=sheet.createRow(r+1);row.setHeightInPoints(24);for(int c=0;c<headers.length;c++)row.createCell(c).setCellValue(examples[r][c]);}
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0,examples.length,0,headers.length-1));
            book.write(out);return out.toByteArray();
        }
    }
}
