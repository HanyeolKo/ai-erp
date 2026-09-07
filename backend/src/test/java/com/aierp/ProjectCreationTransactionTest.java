package com.aierp;

import com.aierp.group.ErpGroupEntity;
import com.aierp.group.ErpGroupRepository;
import com.aierp.group.GroupMemberEntity;
import com.aierp.group.GroupMemberRepository;
import com.aierp.group.GroupRole;
import com.aierp.group.api.GroupAccess;
import com.aierp.identity.api.ApplicationPrincipal;
import com.aierp.identity.api.IdentityProfiles;
import com.aierp.project.ProjectMemberEntity;
import com.aierp.project.ProjectMemberRepository;
import com.aierp.project.ProjectRepository;
import com.aierp.project.api.ProjectController;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true","spring.jpa.properties.hibernate.auto_quote_keyword=true"})
@Import({GroupAccess.class,ProjectController.class,IdentityProfiles.class})
class ProjectCreationTransactionTest {
    @Autowired ProjectController controller;
    @Autowired ProjectRepository projects;
    @Autowired ErpGroupRepository groups;
    @Autowired GroupMemberRepository groupMembers;
    @Autowired TransactionTemplate transactions;
    @MockitoSpyBean ProjectMemberRepository members;

    @Test
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void proxiedCreateRollsBackProjectWhenCreatorMembershipPersistenceFails() {
        var groupId=UUID.randomUUID();
        var owner=UUID.randomUUID();
        var auth=new UsernamePasswordAuthenticationToken(new ApplicationPrincipal(owner,"owner@example.test",true),null,List.of());
        transactions.executeWithoutResult(status -> {
            var group=new ErpGroupEntity();group.id=groupId;group.name="Rollback Group";groups.save(group);
            var membership=new GroupMemberEntity();membership.groupId=groupId;membership.userAccountId=owner;membership.role=GroupRole.OWNER;groupMembers.save(membership);
        });
        doAnswer(invocation -> {
            projects.flush();
            throw new IllegalStateException("membership persistence failure");
        }).when(members).save(any(ProjectMemberEntity.class));

        assertThatThrownBy(() -> controller.create(new ProjectController.CreateRequest(groupId,"Should Roll Back"),auth))
            .isInstanceOf(IllegalStateException.class).hasMessage("membership persistence failure");
        assertThat(projects.findAll()).noneMatch(project -> project.name.equals("Should Roll Back"));
        assertThat(members.findByUserAccountId(owner,PageRequest.of(0,100))).isEmpty();
    }
}
