/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.sql.planner.optimizations;

import com.facebook.presto.SystemSessionProperties;
import com.facebook.presto.sql.planner.assertions.BasePlanTest;
import com.facebook.presto.sql.planner.assertions.PlanMatchPattern;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.testng.annotations.Test;

import static com.facebook.presto.sql.planner.assertions.PlanMatchPattern.anyTree;
import static com.facebook.presto.sql.planner.assertions.PlanMatchPattern.equiJoinClause;
import static com.facebook.presto.sql.planner.assertions.PlanMatchPattern.join;
import static com.facebook.presto.sql.planner.assertions.PlanMatchPattern.tableScan;
import static com.facebook.presto.sql.planner.plan.JoinNode.Type.INNER;

public class TestReorderJoins
        extends BasePlanTest
{
    private static final PlanMatchPattern ORDERS_TABLESCAN = tableScan("orders", ImmutableMap.of("O_ORDERKEY", "orderkey"));
    private static final PlanMatchPattern SUPPLIER_TABLESCAN = tableScan("supplier", ImmutableMap.of("S_SUPPKEY", "suppkey"));
    private static final PlanMatchPattern PART_TABLESCAN = tableScan("part", ImmutableMap.of("P_PARTKEY", "partkey"));
    private static final PlanMatchPattern LINEITEM_TABLESCAN = tableScan(
            "lineitem",
            ImmutableMap.of(
                    "L_PARTKEY", "partkey",
                    "L_ORDERKEY", "orderkey"));

    public TestReorderJoins()
    {
        super(ImmutableMap.of(SystemSessionProperties.REORDER_JOINS, "true"));
    }

    @Test
    public void testEliminateSimpleCrossJoin()
    {
        assertPlan("SELECT o.orderkey FROM part p, orders o, lineitem l WHERE p.partkey = l.partkey AND l.orderkey = o.orderkey",
                anyTree(
                        join(INNER, ImmutableList.of(equiJoinClause("L_ORDERKEY", "O_ORDERKEY")),
                                anyTree(
                                        join(INNER, ImmutableList.of(equiJoinClause("P_PARTKEY", "L_PARTKEY")),
                                                anyTree(PART_TABLESCAN),
                                                anyTree(LINEITEM_TABLESCAN))),
                                anyTree(ORDERS_TABLESCAN))));
    }

    @Test
    public void testGiveUpOnCrossJoin()
    {
        assertPlan("SELECT o.orderkey FROM part p, orders o, lineitem l WHERE l.orderkey = o.orderkey",
                anyTree(
                        join(INNER, ImmutableList.of(equiJoinClause("O_ORDERKEY", "L_ORDERKEY")),
                                anyTree(
                                        join(INNER, ImmutableList.of(),
                                                tableScan("part"),
                                                anyTree(tableScan("orders", ImmutableMap.of("O_ORDERKEY", "orderkey"))))),
                                anyTree(tableScan("lineitem", ImmutableMap.of("L_ORDERKEY", "orderkey"))))));
    }
}