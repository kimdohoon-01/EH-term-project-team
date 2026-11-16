import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.sql.*;
import java.util.*;

/** Run by setup-mysql.ps1. Successful stdout contains credentials and must be captured, never logged. */
class SetupMysql {
    public static void main(String[] args) {
        try {
            Path root=Path.of(args[0]);String host=args[1];int port=Integer.parseInt(args[2]);
            if(!Set.of("127.0.0.1","localhost").contains(host)||port<1||port>65535) throw new IllegalArgumentException("Local MySQL connection required");
            String url="jdbc:mysql://"+host+":"+port+"/?characterEncoding=UTF-8&connectionTimeZone=Asia/Seoul&connectTimeout=5000&socketTimeout=20000";
            Class.forName("com.mysql.cj.jdbc.Driver");
            String adminPassword=Objects.requireNonNullElse(System.getenv("ORDERANDGO_ADMIN_PASSWORD"),"");
            var schemas=List.of("orderandgo_user","orderandgo_order","orderandgo_notification");
            try(Connection db=DriverManager.getConnection(url,args[3],adminPassword);Statement statement=db.createStatement()) {
                if(db.getMetaData().getDatabaseMajorVersion()<8) throw new IllegalArgumentException("MySQL 8 or newer is required");
                String sql=Files.readString(root.resolve("docs/mysql-init.sql"),StandardCharsets.UTF_8).replaceAll("(?m)^--.*$","");
                for(String part:sql.split(";")) if(!part.isBlank()) statement.execute(part.trim());
                String username="orderandgo_"+UUID.randomUUID().toString().substring(0,8);
                byte[] random=new byte[30];new SecureRandom().nextBytes(random);
                String password="Og!9_"+Base64.getUrlEncoder().withoutPadding().encodeToString(random);
                for(String accountHost:List.of("localhost","127.0.0.1")) {
                    String account="'"+username+"'@'"+accountHost+"'";
                    statement.execute("CREATE USER "+account+" IDENTIFIED BY '"+password+"'");
                    for(String schema:schemas) statement.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON `"+schema+"`.* TO "+account);
                }
                try(Connection app=DriverManager.getConnection(url,username,password);Statement check=app.createStatement()) {
                    for(String schema:schemas) check.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='"+schema+"'").close();
                }
                // Only the parent PowerShell script consumes this output and encrypts the password with DPAPI.
                System.out.print("{\"username\":\""+username+"\",\"password\":\""+password+"\"}");
            }
        } catch(SQLException e) {
            System.err.println("MySQL setup failed (code="+e.getErrorCode()+", SQLState="+e.getSQLState()+"). Check the administrator password and CREATE DATABASE / CREATE USER / GRANT permissions.");
            System.exit(1);
        } catch(Exception e) {
            System.err.println("MySQL setup failed: "+e.getClass().getSimpleName()+". Check the connection and setup files.");System.exit(1);
        }
    }
}


