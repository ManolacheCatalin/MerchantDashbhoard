package com.merchant.awinMerchant.todo.dao;

import java.util.List;
import java.util.Optional;

import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import com.merchant.awinMerchant.todo.entity.TodoItem;

@RegisterConstructorMapper(TodoItem.class)
public interface TodoDao {

	/** OPEN items of one merchant, oldest first; {@code id} breaks ties between equal timestamps. */
	@SqlQuery("""
			SELECT id, merchant_id, title, status, created_at
			FROM todo_items
			WHERE merchant_id = :merchantId
			  AND status = 'OPEN'
			ORDER BY created_at, id
			""")
	List<TodoItem> findOpenByMerchant(@Bind("merchantId") long merchantId);

	/**
	 * Marks the item COMPLETED and returns it. Scoped by both id and merchant, so another merchant's item is never
	 * touched. Idempotent: an already COMPLETED item matches again and is returned unchanged. Empty when no item with
	 * this id belongs to the merchant.
	 */
	@SqlQuery("""
			UPDATE todo_items
			SET status = 'COMPLETED'
			WHERE id = :todoId
			  AND merchant_id = :merchantId
			RETURNING id, merchant_id, title, status, created_at
			""")
	Optional<TodoItem> markCompleted(@Bind("merchantId") long merchantId, @Bind("todoId") long todoId);

}
