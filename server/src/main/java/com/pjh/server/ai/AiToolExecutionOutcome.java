package com.pjh.server.ai;

public record AiToolExecutionOutcome(String resultJson, String pendingCompanyDescription) {

    public static AiToolExecutionOutcome result(String resultJson) {
        return new AiToolExecutionOutcome(resultJson, null);
    }

    public static AiToolExecutionOutcome pendingUpdate(String pendingCompanyDescription) {
        return new AiToolExecutionOutcome(null, pendingCompanyDescription);
    }

    public boolean requiresCompanyDescriptionUpdate() {
        return pendingCompanyDescription != null && !pendingCompanyDescription.isBlank();
    }
}
