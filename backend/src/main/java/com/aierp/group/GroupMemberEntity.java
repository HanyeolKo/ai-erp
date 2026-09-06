package com.aierp.group;
import jakarta.persistence.*;
import java.util.*;
import java.io.Serializable;
@Entity @Table(schema="\"group\"",name="group_member") @IdClass(GroupMemberEntity.Key.class)
public class GroupMemberEntity {
    @Id public UUID groupId; @Id public UUID userAccountId;
    public static class Key implements Serializable {
        public UUID groupId;public UUID userAccountId;
        @Override public boolean equals(Object other) {return other instanceof Key k && Objects.equals(groupId,k.groupId) && Objects.equals(userAccountId,k.userAccountId);}
        @Override public int hashCode() {return Objects.hash(groupId,userAccountId);}
    }
}
