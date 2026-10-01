package org.tkit.onecx.ai.provider.domain.daos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import org.tkit.onecx.ai.provider.domain.models.AgentMcpToolRule;
import org.tkit.quarkus.jpa.daos.AbstractDAO;
import org.tkit.quarkus.jpa.exceptions.DAOException;

@ApplicationScoped
public class AgentMcpToolRuleDAO extends AbstractDAO<AgentMcpToolRule> {

    private static final String AGENT_FIELD = "agent";
    private static final String GLOBAL_TOOL_FIELD = "globalTool";
    private static final String STORED_TOOL_PERMISSIONS = "SELECT guid, allowed FROM agent_mcp_tool_rule WHERE agent_id = :agentId";
    private static final String STORED_EXECUTION_POLICIES = """
            SELECT t.guid, t.execution_policy
            FROM agent_tool_rl atr
            JOIN tool t ON t.guid = atr.tool_id
            WHERE atr.agent_id = :agentId
            UNION ALL
            SELECT gt.guid, gt.execution_policy
            FROM agent_global_tool_rl agtr
            JOIN global_tool gt ON gt.guid = agtr.global_tool_id
            WHERE agtr.agent_id = :agentId
            """;

    /**
     * @return raw {@code ALLOWED} values by rule id for the agent
     */
    public Map<String, String> findStoredToolPermissionsByAgentId(String agentId) {
        if (agentId == null) {
            return Map.of();
        }
        try {
            var rows = this.getEntityManager().createNativeQuery(STORED_TOOL_PERMISSIONS)
                    .setParameter("agentId", agentId)
                    .getResultList();
            var result = new HashMap<String, String>();
            for (Object row : rows) {
                var columns = (Object[]) row;
                result.put(String.valueOf(columns[0]), columns[1] == null ? null : columns[1].toString());
            }
            return result;
        } catch (Exception ex) {
            throw new DAOException(ErrorKeys.ERROR_FIND_STORED_TOOL_PERMISSIONS_BY_AGENT_ID, ex);
        }
    }

    /**
     * @return raw {@code EXECUTION_POLICY} values by tool id for tools assigned to the agent
     */
    public Map<String, String> findStoredExecutionPoliciesByAgentId(String agentId) {
        if (agentId == null) {
            return Map.of();
        }
        try {
            var rows = this.getEntityManager().createNativeQuery(STORED_EXECUTION_POLICIES)
                    .setParameter("agentId", agentId)
                    .getResultList();
            var result = new HashMap<String, String>();
            for (Object row : rows) {
                var columns = (Object[]) row;
                result.put(String.valueOf(columns[0]), columns[1] == null ? null : columns[1].toString());
            }
            return result;
        } catch (Exception ex) {
            throw new DAOException(ErrorKeys.ERROR_FIND_STORED_EXECUTION_POLICIES_BY_AGENT_ID, ex);
        }
    }

    public List<AgentMcpToolRule> findByAgentId(String agentId) {
        try {
            var cb = this.getEntityManager().getCriteriaBuilder();
            var cq = cb.createQuery(AgentMcpToolRule.class);
            var root = cq.from(AgentMcpToolRule.class);
            cq.where(cb.equal(root.get(AGENT_FIELD).get("id"), agentId));
            return this.getEntityManager().createQuery(cq).getResultList();
        } catch (Exception ex) {
            throw new DAOException(ErrorKeys.ERROR_FIND_RULES_BY_AGENT_ID, ex);
        }
    }

    public List<AgentMcpToolRule> findByAgentAndToolId(String agentId, String toolId) {
        try {
            var cb = this.getEntityManager().getCriteriaBuilder();
            var cq = cb.createQuery(AgentMcpToolRule.class);
            var root = cq.from(AgentMcpToolRule.class);
            cq.where(cb.and(
                    cb.equal(root.get(AGENT_FIELD).get("id"), agentId),
                    cb.or(
                            cb.equal(root.get("tool").get("id"), toolId),
                            cb.equal(root.get(GLOBAL_TOOL_FIELD).get("id"), toolId))));
            return this.getEntityManager().createQuery(cq).getResultList();
        } catch (Exception ex) {
            throw new DAOException(ErrorKeys.ERROR_FIND_RULES_BY_AGENT_AND_TOOL_ID, ex);
        }
    }

    public List<AgentMcpToolRule> findByAgentAndToolIds(String agentId, List<String> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return List.of();
        }
        try {
            var cb = this.getEntityManager().getCriteriaBuilder();
            var cq = cb.createQuery(AgentMcpToolRule.class);
            var root = cq.from(AgentMcpToolRule.class);
            cq.where(cb.and(
                    cb.equal(root.get(AGENT_FIELD).get("id"), agentId),
                    root.get("tool").get("id").in(toolIds)));
            return this.getEntityManager().createQuery(cq).getResultList();
        } catch (Exception ex) {
            throw new DAOException(ErrorKeys.ERROR_FIND_RULES_BY_AGENT_AND_TOOL_IDS, ex);
        }
    }

    public List<AgentMcpToolRule> findByAgentAndGlobalToolIds(String agentId, List<String> globalToolIds) {
        if (globalToolIds == null || globalToolIds.isEmpty()) {
            return List.of();
        }
        try {
            var cb = this.getEntityManager().getCriteriaBuilder();
            var cq = cb.createQuery(AgentMcpToolRule.class);
            var root = cq.from(AgentMcpToolRule.class);
            cq.where(cb.and(
                    cb.equal(root.get(AGENT_FIELD).get("id"), agentId),
                    root.get(GLOBAL_TOOL_FIELD).get("id").in(globalToolIds)));
            return this.getEntityManager().createQuery(cq).getResultList();
        } catch (Exception ex) {
            throw new DAOException(ErrorKeys.ERROR_FIND_RULES_BY_AGENT_AND_GLOBAL_TOOL_IDS, ex);
        }
    }

    @Transactional
    public void deleteByAgentId(String agentId) {
        try {
            var cb = this.getEntityManager().getCriteriaBuilder();
            var cd = cb.createCriteriaDelete(AgentMcpToolRule.class);
            var root = cd.from(AgentMcpToolRule.class);
            cd.where(cb.equal(root.get(AGENT_FIELD).get("id"), agentId));
            this.getEntityManager().createQuery(cd).executeUpdate();
        } catch (Exception ex) {
            throw new DAOException(ErrorKeys.ERROR_DELETE_RULES_BY_AGENT_ID, ex);
        }
    }

    @Transactional
    public void deleteByAgentAndToolId(String agentId, String toolId) {
        try {
            var cb = this.getEntityManager().getCriteriaBuilder();
            var cd = cb.createCriteriaDelete(AgentMcpToolRule.class);
            var root = cd.from(AgentMcpToolRule.class);
            cd.where(cb.and(
                    cb.equal(root.get(AGENT_FIELD).get("id"), agentId),
                    cb.equal(root.get("tool").get("id"), toolId)));
            this.getEntityManager().createQuery(cd).executeUpdate();
        } catch (Exception ex) {
            throw new DAOException(ErrorKeys.ERROR_DELETE_RULES_BY_AGENT_AND_TOOL_ID, ex);
        }
    }

    @Transactional
    public void deleteByToolId(String toolId) {
        try {
            var cb = this.getEntityManager().getCriteriaBuilder();
            var cd = cb.createCriteriaDelete(AgentMcpToolRule.class);
            var root = cd.from(AgentMcpToolRule.class);
            cd.where(cb.equal(root.get("tool").get("id"), toolId));
            this.getEntityManager().createQuery(cd).executeUpdate();
        } catch (Exception ex) {
            throw new DAOException(ErrorKeys.ERROR_DELETE_RULES_BY_TOOL_ID, ex);
        }
    }

    @Transactional
    public void deleteByGlobalToolId(String globalToolId) {
        try {
            var cb = this.getEntityManager().getCriteriaBuilder();
            var cd = cb.createCriteriaDelete(AgentMcpToolRule.class);
            var root = cd.from(AgentMcpToolRule.class);
            cd.where(cb.equal(root.get(GLOBAL_TOOL_FIELD).get("id"), globalToolId));
            this.getEntityManager().createQuery(cd).executeUpdate();
        } catch (Exception ex) {
            throw new DAOException(ErrorKeys.ERROR_DELETE_RULES_BY_GLOBAL_TOOL_ID, ex);
        }
    }

    public enum ErrorKeys {
        ERROR_FIND_RULES_BY_AGENT_ID,
        ERROR_FIND_RULES_BY_AGENT_AND_TOOL_ID,
        ERROR_FIND_RULES_BY_AGENT_AND_TOOL_IDS,
        ERROR_FIND_RULES_BY_AGENT_AND_GLOBAL_TOOL_IDS,
        ERROR_FIND_STORED_TOOL_PERMISSIONS_BY_AGENT_ID,
        ERROR_FIND_STORED_EXECUTION_POLICIES_BY_AGENT_ID,
        ERROR_DELETE_RULES_BY_AGENT_ID,
        ERROR_DELETE_RULES_BY_AGENT_AND_TOOL_ID,
        ERROR_DELETE_RULES_BY_TOOL_ID,
        ERROR_DELETE_RULES_BY_GLOBAL_TOOL_ID,
    }
}
