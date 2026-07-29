-- Test-only HAProxy arrival logger. This file is loaded once into HAProxy's
-- shared Lua context. haproxy.cfg intentionally uses nbthread 1, so increment
-- and emission happen in one total order.

local arrival_sequence = 0

local function json_escape(value)
    value = value or ""
    value = string.gsub(value, "\\", "\\\\")
    value = string.gsub(value, "\"", "\\\"")
    value = string.gsub(value, "\b", "\\b")
    value = string.gsub(value, "\f", "\\f")
    value = string.gsub(value, "\n", "\\n")
    value = string.gsub(value, "\r", "\\r")
    value = string.gsub(value, "\t", "\\t")
    value = string.gsub(value, "[%z\1-\8\11\12\14-\31]", function(character)
        return string.format("\\u%04x", string.byte(character))
    end)
    return value
end

core.register_action("record_arrival", { "http-req" }, function(txn)
    arrival_sequence = arrival_sequence + 1

    local run_id = txn.f:req_hdr("X-Run-Id")
    local request_id = txn.f:req_hdr("X-Request-Id")
    local event = string.format(
        "FIFO_HAPROXY_ACCEPTED {\"arrival_sequence\":%d,\"run_id\":\"%s\",\"request_id\":\"%s\"}",
        arrival_sequence,
        json_escape(run_id),
        json_escape(request_id)
    )
    core.Info(event)
end)
