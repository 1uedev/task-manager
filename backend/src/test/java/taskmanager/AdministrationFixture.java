package taskmanager;

import java.util.Arrays;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import taskmanager.platform.AdministrationRight;
import taskmanager.platform.CompanyContext;

/**
 * Gives test accounts company rights the way the start of an installation does, but directly in the tables: no HTTP
 * endpoint for rights exists yet (TASK-030). Every call adds a group of its own, so a test can hand out exactly the rights it
 * wants. The rights version moves with each grant, like a change through the permissions module, and no audit entry is written.
 */
public final class AdministrationFixture {
    private AdministrationFixture() {
    }

    /** The accounts that administer the company: the right to manage rights and every administrative right. */
    public static void grantAdministration(JdbcTemplate jdbc, CompanyContext company, PlatformTransactionManager transactions, UUID account) {
        grant(jdbc, company, transactions, account, true, AdministrationRight.values());
    }

    /** Exactly these administrative rights, and not the right to manage rights. */
    public static void grant(JdbcTemplate jdbc, CompanyContext company, PlatformTransactionManager transactions, UUID account,
            AdministrationRight... rights) {
        grant(jdbc, company, transactions, account, false, rights);
    }

    /** Takes every group membership of the account away, like a rights change that withdraws all of its rights. */
    public static void withdrawAll(JdbcTemplate jdbc, CompanyContext company, PlatformTransactionManager transactions, UUID account) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.update("delete from permissions_membership where user_id = ?", account);
            jdbc.update("update permissions_revision set revision = revision + 1 where company_id = ?", company.find().orElseThrow());
        });
    }

    /** The right to manage rights, if asked for, together with exactly these administrative rights. */
    public static void grant(JdbcTemplate jdbc, CompanyContext company, PlatformTransactionManager transactions, UUID account,
            boolean manage, AdministrationRight... rights) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            UUID companyId = company.find().orElseGet(company::create);
            jdbc.update("insert into permissions_revision(company_id) values (?) on conflict do nothing", companyId);
            UUID group = UUID.randomUUID();
            jdbc.update("insert into permissions_group(company_id, id, name) values (?, ?, ?)", companyId, group, "fixture-" + group);
            if (manage) {
                jdbc.update("insert into permissions_action(company_id, group_id, project_id, action_key) values (?, ?, null, 'permissions.manage')", companyId, group);
            }
            Arrays.stream(rights).forEach(right -> jdbc.update(
                "insert into permissions_action(company_id, group_id, project_id, action_key) values (?, ?, null, ?)", companyId, group, right.key()));
            jdbc.update("insert into permissions_membership(company_id, group_id, user_id) values (?, ?, ?)", companyId, group, account);
            jdbc.update("update permissions_revision set revision = revision + 1 where company_id = ?", companyId);
        });
    }
}
