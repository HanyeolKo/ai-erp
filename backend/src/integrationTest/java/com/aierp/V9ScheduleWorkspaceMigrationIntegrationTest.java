package com.aierp;

import static org.assertj.core.api.Assertions.assertThat;
import java.sql.*; import java.time.Instant; import java.util.UUID;
import org.flywaydb.core.Flyway; import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer; import org.testcontainers.junit.jupiter.Container; import org.testcontainers.junit.jupiter.Testcontainers;

/** Real PostgreSQL upgrade proof for the additive schedule workspace boundary. */
@Testcontainers
class V9ScheduleWorkspaceMigrationIntegrationTest {
    @Container static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:18.6");
    @Test void v8RowsSurviveAndWorkspaceTablesAreEmptyAndConstrained() throws Exception {
        var database="aierp_v8_to_v9_"+UUID.randomUUID().toString().replace("-","");
        var adminUrl="jdbc:postgresql://%s:%d/postgres".formatted(postgres.getHost(),postgres.getMappedPort(5432));
        var databaseUrl="jdbc:postgresql://%s:%d/%s".formatted(postgres.getHost(),postgres.getMappedPort(5432),database);
        try(var admin=DriverManager.getConnection(adminUrl,postgres.getUsername(),postgres.getPassword())){admin.createStatement().execute("CREATE DATABASE "+identifier(database));}
        var user=UUID.randomUUID();var group=UUID.randomUUID();var project=UUID.randomUUID();var schedule=UUID.randomUUID();
        try {
            Flyway.configure().dataSource(databaseUrl,postgres.getUsername(),postgres.getPassword()).locations("classpath:db/migration").target("8").load().migrate();
            try(var db=DriverManager.getConnection(databaseUrl,postgres.getUsername(),postgres.getPassword())){execute(db,"INSERT INTO identity.user_account(id,email,display_name) VALUES (?,?,?)",user,"legacy@example.test","Legacy");execute(db,"INSERT INTO \"group\".erp_group(id,name) VALUES (?,?)",group,"Legacy");execute(db,"INSERT INTO project.project(id,group_id,name) VALUES (?,?,?)",project,group,"Legacy project");execute(db,"INSERT INTO project.project_member(project_id,user_account_id,role) VALUES (?,?,'MANAGER')",project,user);execute(db,"INSERT INTO schedule.project_schedule(id,project_id,created_by,title,starts_at,ends_at,status) VALUES (?,?,?,'Legacy',?,?,'CONFIRMED')",schedule,project,user,Timestamp.from(Instant.parse("2099-01-01T10:00:00Z")),Timestamp.from(Instant.parse("2099-01-01T11:00:00Z")));}
            Flyway.configure().dataSource(databaseUrl,postgres.getUsername(),postgres.getPassword()).locations("classpath:db/migration").target("9").load().migrate();
            try(var db=DriverManager.getConnection(databaseUrl,postgres.getUsername(),postgres.getPassword())){assertThat(scalar(db,"select title from schedule.project_schedule where id=?",schedule)).isEqualTo("Legacy");assertThat(scalar(db,"select count(*) from schedule.schedule_property")).isEqualTo(0L);assertThat(scalar(db,"select count(*) from schedule.schedule_saved_view")).isEqualTo(0L);assertThat(scalar(db,"select column_name from information_schema.columns where table_schema='schedule' and table_name='schedule_property_value' and column_name='option_id'")).isEqualTo("option_id");}
        } finally {try(var admin=DriverManager.getConnection(adminUrl,postgres.getUsername(),postgres.getPassword())){admin.createStatement().execute("DROP DATABASE IF EXISTS "+identifier(database));}}
    }
    private static void execute(Connection db,String sql,Object... values)throws SQLException{try(var s=db.prepareStatement(sql)){for(int i=0;i<values.length;i++)s.setObject(i+1,values[i]);s.executeUpdate();}}
    private static Object scalar(Connection db,String sql,Object... values)throws SQLException{try(var s=db.prepareStatement(sql)){for(int i=0;i<values.length;i++)s.setObject(i+1,values[i]);try(var r=s.executeQuery()){return r.next()?r.getObject(1):null;}}}
    private static String identifier(String value){return "\""+value.replace("\"","\"\"")+"\"";}
}
