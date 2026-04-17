package com.pjh.server.common;

public final class AiConstants {

    private AiConstants() {
    }

    public static final String MESSAGE_TYPE_TEXT = "text";
    public static final String MESSAGE_TYPE_MARKDOWN = "markdown";
    public static final String MESSAGE_TYPE_ACTION_REQUIRED = "action_required";
    public static final String MESSAGE_TYPE_ACTION_RESULT = "action_result";

    public static final String CHAT_RESULT_MESSAGE = "message";
    public static final String CHAT_RESULT_ACTION_REQUIRED = "action_required";

    public static final String STREAM_EVENT_START = "start";
    public static final String STREAM_EVENT_TOKEN = "token";
    public static final String STREAM_EVENT_ACTION_REQUIRED = "action_required";
    public static final String STREAM_EVENT_ERROR = "error";
    public static final String STREAM_EVENT_DONE = "done";

    public static final String STREAM_DONE_REASON_MESSAGE = "message";
    public static final String STREAM_DONE_REASON_ACTION_REQUIRED = "action_required";
    public static final String STREAM_DONE_REASON_ERROR = "error";

    public static final String ACTION_TYPE_UPDATE_COMPANY_DESCRIPTION = "update_company_description";

    public static final String ACTION_STATUS_PENDING = "pending";
    public static final String ACTION_STATUS_APPROVED = "approved";
    public static final String ACTION_STATUS_REJECTED = "rejected";
    public static final String ACTION_STATUS_EXPIRED = "expired";

    public static final int CHAT_MEMORY_WINDOW_ROUNDS = 5;
    public static final int CHAT_MAX_TOOL_ROUNDS = 4;
    public static final int CHAT_TITLE_MAX_LENGTH = 20;
    public static final int HITL_TOKEN_TTL_MINUTES = 5;
}
