package com.cleanroute.notification.repository;

import com.cleanroute.notification.domain.NotificationModels.NotificationDraft;
import com.cleanroute.notification.domain.NotificationModels.UserNotification;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public class NotificationRepository {
    private final JdbcTemplate jdbc;
    public NotificationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void insert(UUID userId, NotificationDraft draft) {
        try {
            jdbc.update("INSERT INTO user_notification(id,user_id,kind,dedup_key,title,message) VALUES (?,?,?,?,?,?)",
                    UUID.randomUUID(), userId, draft.kind(), draft.dedupKey(), draft.title(), draft.message());
        } catch (DuplicateKeyException alreadyDelivered) {
            // A dashboard refresh or repeated route submission must not duplicate an alert.
        }
    }

    public List<UserNotification> list(UUID userId, int limit) {
        return jdbc.query("SELECT id,kind,title,message,created_at,read_at FROM user_notification WHERE user_id=? ORDER BY created_at DESC,id DESC LIMIT ?",
                (rs, row) -> new UserNotification(rs.getObject("id", UUID.class), rs.getString("kind"),
                        rs.getString("title"), rs.getString("message"), rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("read_at") == null ? null : rs.getTimestamp("read_at").toInstant()), userId, limit);
    }

    public int unreadCount(UUID userId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM user_notification WHERE user_id=? AND read_at IS NULL", Integer.class, userId);
        return count == null ? 0 : count;
    }

    public java.util.Optional<UserNotification> find(UUID id, UUID userId) {
        return jdbc.query("SELECT id,kind,title,message,created_at,read_at FROM user_notification WHERE id=? AND user_id=?",
                (rs, row) -> new UserNotification(rs.getObject("id", UUID.class), rs.getString("kind"),
                        rs.getString("title"), rs.getString("message"), rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("read_at") == null ? null : rs.getTimestamp("read_at").toInstant()),
                id, userId).stream().findFirst();
    }

    public boolean markRead(UUID id, UUID userId) {
        return jdbc.update("UPDATE user_notification SET read_at=COALESCE(read_at,CURRENT_TIMESTAMP) WHERE id=? AND user_id=?", id, userId) > 0;
    }
}
