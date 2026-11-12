package taskmanager.identity.directory;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.naming.directory.BasicAttribute;
import tools.jackson.databind.json.JsonMapper;

/** Finite collision search and legacy text comparison. No replacement of product code. */
public class IdentifierProbe {
    static String key(byte[]bytes)throws Exception{return DirectoryClient.identifier(new BasicAttribute("id",bytes));}
    public static void main(String[]args)throws Exception{
        var result=new LinkedHashMap<String,Object>();var seen=new HashMap<String,String>();int checked=0,collisions=0;
        for(int length=1;length<=2;length++)for(int n=0;n<(length==1?256:65536);n++){
            byte[]b=length==1?new byte[]{(byte)n}:new byte[]{(byte)(n>>8),(byte)n};String k=key(b),hex=HexFormat.of().formatHex(b);checked++;
            if(k!=null){String old=seen.putIfAbsent(k,hex);if(old!=null&&!old.equals(hex))collisions++;}
        }
        var samples=new ArrayList<Map<String,Object>>();
        for(String text:List.of("entry-uuid-123","Müller-17"," id ","id","  ","hex:80","HEX:80","\u0001id","id\u0085","é","e\u0301","😀","\u200b","\ufeff","x\ufffdy")){
            byte[]b=text.getBytes(StandardCharsets.UTF_8);String now=key(b),before=text.trim();if(before.isEmpty())before=null;
            var row=new LinkedHashMap<String,Object>();row.put("rawHex",HexFormat.of().formatHex(b));row.put("oldKey",before);row.put("newKey",now);row.put("unchanged",Objects.equals(before,now));samples.add(row);
            if(now!=null){String old=seen.putIfAbsent(now,HexFormat.of().formatHex(b));if(old!=null&&!old.equals(HexFormat.of().formatHex(b)))collisions++;}
        }
        var multi=new BasicAttribute("id",true);multi.add("same");multi.add("same");
        result.put("byteStringsChecked",checked);result.put("collisionsFound",collisions);result.put("samples",samples);
        result.put("multipleIdenticalValuesRefused",DirectoryClient.identifier(multi)==null);
        result.put("lossyStringRefused",DirectoryClient.identifier(new BasicAttribute("id","x\ufffdy"))==null);
        result.put("invalidUtf8Key",key(new byte[]{(byte)0xc3,0x28}));
        result.put("unpairedSurrogateStringKey",DirectoryClient.identifier(new BasicAttribute("id","x\ud800y")));
        Files.writeString(Path.of(args[0]),JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsString(result)+"\n");System.out.println("REVIEW bytes="+checked+" collisions="+collisions);
    }
}
