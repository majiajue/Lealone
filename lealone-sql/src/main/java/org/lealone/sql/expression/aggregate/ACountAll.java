/*
 * Copyright Lealone Database Group.
 * Licensed under the Server Side Public License, v 1.
 * Initial Developer: zhh
 */
package org.lealone.sql.expression.aggregate;

import org.lealone.common.exceptions.DbException;
import org.lealone.db.session.ServerSession;
import org.lealone.db.value.Value;
import org.lealone.db.value.ValueLong;
import org.lealone.sql.expression.Expression;
import org.lealone.sql.query.Select;

// COUNT(*)
public class ACountAll extends BuiltInAggregate {

    public ACountAll(int type, Expression on, Select select, boolean distinct) {
        super(type, on, select, distinct);
        // 在Parser.readAggregate那里确保使用COUNT_ALL时distinct是false
        if (distinct) {
            throw DbException.getInternalError();
        }
    }

    @Override
    public Expression optimize(ServerSession session) {
        super.optimize(session);
        dataType = Value.LONG;
        scale = 0;
        precision = ValueLong.PRECISION;
        displaySize = ValueLong.DISPLAY_SIZE;
        return this;
    }

    @Override
    protected AggregateData createAggregateData() {
        return new AggregateDataCountAll();
    }

    @Override
    public String getSQL() {
        return "COUNT(*)";
    }

    public class AggregateDataCountAll extends AggregateData {

        private long count;

        public long getCount() {
            return count;
        }

        public void setCount(long count) {
            this.count = count;
        }

        public Select getSelect() {
            return select;
        }

        @Override
        public void add(ServerSession session, Value v) {
            count++;
        }

        @Override
        Value getValue(ServerSession session) {
            return ValueLong.get(count);
        }
    }
}
