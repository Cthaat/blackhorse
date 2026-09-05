package com.ruoyi.lab.mapper;

import java.util.*;
import org.apache.ibatis.annotations.*;

/** Closed SQL templates. Candidate IDs are never exposed until business-service authorization succeeds. */
public interface TaskSourceMapper
{
    class Origin { public Long id; public Long laboratoryId; public Long departmentId; }
    @SelectProvider(type=Sql.class,method="maximum") long maximum(@Param("kind")String kind);
    @SelectProvider(type=Sql.class,method="batch") List<Origin> batch(@Param("kind")String kind,@Param("after")long after,
            @Param("maximum")long maximum,@Param("filters")Map<String,String> filters);
    @SelectProvider(type=Sql.class,method="origin") Origin origin(@Param("kind")String kind,@Param("id")long id);
    final class Sql
    {
        private static String from(String kind)
        {
            return switch(kind) {
                case "LABORATORY" -> "lab_laboratory x join lab_laboratory l on l.id=x.id";
                case "DEVICE" -> "lab_device x join lab_laboratory l on l.id=x.laboratory_id";
                case "RESERVATION" -> "lab_reservation x join lab_device d on d.id=x.device_id join lab_laboratory l on l.id=d.laboratory_id";
                case "REPAIR" -> "lab_repair_order x join lab_device d on d.id=x.device_id join lab_laboratory l on l.id=d.laboratory_id";
                case "HAZARD" -> "lab_hazard x left join lab_device d on x.target_type='DEVICE' and d.id=x.target_id join lab_laboratory l on l.id=case when x.target_type='LABORATORY' then x.target_id else d.laboratory_id end";
                default -> throw new IllegalArgumentException("任务类型无效");
            };
        }
        public static String maximum(Map<String,Object> p){return "select coalesce(max(x.id),0) from "+from((String)p.get("kind"))+" where x.del_flag='0'";}
        public static String origin(Map<String,Object> p){return "select x.id,l.id as laboratoryId,l.dept_id as departmentId from "+from((String)p.get("kind"))+" where x.id=#{id} and x.del_flag='0' and l.del_flag='0'";}
        public static String batch(Map<String,Object> p)
        {
            String kind=(String)p.get("kind");
            String sql="<script>select x.id,l.id as laboratoryId,l.dept_id as departmentId from "+from(kind)+" where x.del_flag='0' and l.del_flag='0' and x.id &gt; #{after} and x.id &lt;= #{maximum}";
            Map<String,String> columns=new LinkedHashMap<>();columns.put("status","x.status");
            switch(kind) {
                case "DEVICE" -> {columns.put("laboratoryId","x.laboratory_id");columns.put("categoryCode","x.category_code");}
                case "RESERVATION" -> {columns.put("deviceId","x.device_id");columns.put("applicantId","x.applicant_id");}
                case "REPAIR" -> columns.put("deviceId","x.device_id");
                case "HAZARD" -> {columns.put("severity","x.severity");columns.put("ownerId","x.owner_id");}
                default -> { }
            }
            for(var c:columns.entrySet())sql+="<if test=\"filters."+c.getKey()+" != null and filters."+c.getKey()+" != ''\"> and "+c.getValue()+"=#{filters."+c.getKey()+"}</if>";
            String key=switch(kind){case "LABORATORY","DEVICE"->"keyword";case "RESERVATION"->"reservationNo";case "REPAIR"->"repairNo";default->null;};
            String column=switch(kind){case "LABORATORY","DEVICE"->"x.name";case "RESERVATION"->"x.reservation_no";case "REPAIR"->"x.repair_no";default->null;};
            if(key!=null) {
                String expr=column+" like concat('%',#{filters."+key+"},'%')";
                if(kind.equals("RESERVATION"))expr="x.reservation_no=#{filters.reservationNo}";
                if(kind.equals("REPAIR"))expr="x.repair_no=#{filters.repairNo}";
                if(kind.equals("LABORATORY"))expr+=" or x.lab_code like concat('%',#{filters.keyword},'%')";
                if(kind.equals("DEVICE"))expr+=" or x.asset_no like concat('%',#{filters.keyword},'%')";
                sql+="<if test=\"filters."+key+" != null and filters."+key+" != ''\"> and ("+expr+")</if>";
            }
            if(kind.equals("RESERVATION"))sql+="<if test=\"filters.from != null and filters.from != ''\"> and x.end_time &gt; #{filters.from}</if><if test=\"filters.to != null and filters.to != ''\"> and x.start_time &lt; #{filters.to}</if>";
            return sql+" order by x.id limit 100</script>";
        }
    }
}
