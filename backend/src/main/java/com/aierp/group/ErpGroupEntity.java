package com.aierp.group;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(schema="\"group\"",name="erp_group")
public class ErpGroupEntity {
    @Id public UUID id;
    public String name;
}
