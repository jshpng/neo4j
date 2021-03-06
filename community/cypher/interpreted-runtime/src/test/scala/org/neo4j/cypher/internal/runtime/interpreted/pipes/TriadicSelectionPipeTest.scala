/*
 * Copyright (c) 2002-2020 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.internal.runtime.interpreted.pipes

import org.neo4j.cypher.internal.runtime.ExecutionContext
import org.neo4j.cypher.internal.runtime.interpreted.QueryStateHelper
import org.neo4j.cypher.internal.v4_0.util.attribution.Id
import org.neo4j.cypher.internal.v4_0.util.test_helpers.CypherFunSuite
import org.neo4j.kernel.impl.core.NodeEntity
import org.neo4j.kernel.impl.util.ValueUtils
import org.neo4j.values.AnyValue
import org.neo4j.values.virtual.NodeValue

import scala.collection.{Map, mutable}

class TriadicSelectionPipeTest extends CypherFunSuite {
  test("triadic from input with no cycles") {
    val left = createFakePipeWith(Array("a", "b"), 0 -> List(1, 2))
    val right = createFakeArgumentPipeWith(Array("b", "c"),
      1 -> List(11, 12),
      2 -> List(21, 22)
    )
    val pipe = TriadicSelectionPipe(positivePredicate = false, left, "a", "b", "c", right)()
    val queryState = QueryStateHelper.empty
    val ids = pipe.createResults(queryState).map(ctx => ctx.getByName("c")).map { case y: NodeValue =>
      y.id()
    }.toSet
    ids should equal(Set(11, 12, 21, 22))
  }

  test("triadic from input with cycles and negative predicate") {
    val left = createFakePipeWith(Array("a", "b"), 0 -> List(1, 2))
    val right = createFakeArgumentPipeWith(Array("b", "c"),
      1 -> List(11, 12, 2),
      2 -> List(21, 22)
    )
    val pipe = TriadicSelectionPipe(positivePredicate = false, left, "a", "b", "c", right)()
    val queryState = QueryStateHelper.empty
    val ids = pipe.createResults(queryState).map(ctx => ctx.getByName("c")).map { case y: NodeValue =>
      y.id()
    }.toSet
    ids should equal(Set(11, 12, 21, 22))
  }

  test("triadic from input with cycles and positive predicate") {
    val left = createFakePipeWith(Array("a", "b"), 0 -> List(1, 2))
    val right = createFakeArgumentPipeWith(Array("b", "c"),
      1 -> List(11, 12, 2),
      2 -> List(21, 22)
    )
    val pipe = TriadicSelectionPipe(positivePredicate = true, left, "a", "b", "c", right)()
    val queryState = QueryStateHelper.empty
    val ids = pipe.createResults(queryState).map(ctx => ctx.getByName("c")).map { case y: NodeValue =>
      y.id()
    }.toSet
    ids should equal(Set(2))
  }

  test("triadic from input with two different sources and no cycles") {
    val left = createFakePipeWith(Array("a", "b"), 0 -> List(1, 2), 3 -> List(2, 4))
    val right = createFakeArgumentPipeWith(Array("b", "c"),
      1 -> List(11, 12),
      2 -> List(21, 22),
      4 -> List(41, 42)
    )
    val pipe = TriadicSelectionPipe(positivePredicate = false, left, "a", "b", "c", right)()
    val queryState = QueryStateHelper.empty
    val ids = pipe.createResults(queryState).map(ctx => (ctx.getByName("a"), ctx.getByName("c"))).map {
      case (a: NodeValue, c: NodeValue) =>
        (a.id(), c.id())
    }.toSet
    ids should equal(Set((0, 11), (0, 12), (0, 21), (0, 22), (3, 21), (3, 22), (3, 41), (3, 42)))
  }

  test("triadic from input with two different sources and cycles with negative predicate") {
    val left = createFakePipeWith(Array("a", "b"), 0 -> List(1, 2), 3 -> List(2, 4))
    val right = createFakeArgumentPipeWith(Array("b", "c"),
      1 -> List(11, 12, 2), // same 'a' so should fail predicate
      2 -> List(21, 22),
      4 -> List(41, 42, 1) // different 'a' so should pass predicate
    )
    val pipe = TriadicSelectionPipe(positivePredicate = false, left, "a", "b", "c", right)()
    val queryState = QueryStateHelper.empty
    val ids = pipe.createResults(queryState).map(ctx => (ctx.getByName("a"), ctx.getByName("c"))).map {
      case (a: NodeValue, c: NodeValue) =>
        (a.id(), c.id())
    }.toSet
    ids should equal(Set((0, 11), (0, 12), (0, 21), (0, 22), (3, 1), (3, 21), (3, 22), (3, 41), (3, 42)))
  }

  test("triadic from input with two different sources and cycles with positive predicate") {
    val left = createFakePipeWith(Array("a", "b"), 0 -> List(1, 2), 3 -> List(2, 4))
    val right = createFakeArgumentPipeWith(Array("b", "c"),
      1 -> List(11, 12, 2), // same 'a' so should pass predicate
      2 -> List(21, 22),
      4 -> List(41, 42, 1) // different 'a' so should fail predicate
    )
    val pipe = TriadicSelectionPipe(positivePredicate = true, left, "a", "b", "c", right)()
    val queryState = QueryStateHelper.empty
    val ids = pipe.createResults(queryState).map(ctx => (ctx.getByName("a"), ctx.getByName("c"))).map {
      case (a: NodeValue, c: NodeValue) =>
        (a.id(), c.id())
    }.toSet
    ids should equal(Set((0, 2)))
  }

  test("triadic from input with repeats") {
    val left = createFakePipeWith(Array("a", "b"), 0 -> List(1, 2, 1), 3 -> List(2, 4, 4))
    val right = createFakeArgumentPipeWith(Array("b", "c"),
      1 -> List(11, 12),
      2 -> List(21, 22),
      4 -> List(41, 42)
    )
    val pipe = TriadicSelectionPipe(positivePredicate = false, left, "a", "b", "c", right)()
    val queryState = QueryStateHelper.empty
    val ids = pipe.createResults(queryState).map(ctx => (ctx.getByName("a"), ctx.getByName("c"))).map {
      case (a: NodeValue, c: NodeValue) =>
        (a.id, c.id)
    }.toSet
    ids should equal(Set((0, 11), (0, 12), (0, 21), (0, 22), (3, 21), (3, 22), (3, 41), (3, 42)))
  }

  test("triadic ignores nulls") {
    val left = createFakePipeWith(Array("a", "b"), 0 -> List(1, 2, null), 3 -> List(2, null, 4))
    val right = createFakeArgumentPipeWith(Array("b", "c"),
      1 -> List(11, 12),
      2 -> List(21, 22),
      4 -> List(41, 42)
    )
    val pipe = TriadicSelectionPipe(positivePredicate = false, left, "a", "b", "c", right)()
    val queryState = QueryStateHelper.empty
    val ids = pipe.createResults(queryState).map(ctx => (ctx.getByName("a"), ctx.getByName("c"))).map {
      case (a: NodeValue, c: NodeValue) =>
        (a.id, c.id())
    }.toSet
    ids should equal(Set((0, 11), (0, 12), (0, 21), (0, 22), (3, 21), (3, 22), (3, 41), (3, 42)))
  }

  private def createFakeDataWith(keys: Array[String], data: (Int, List[Any])*) = {
    def nodeWithId(id: Long) = {
      new NodeEntity(null, id)
    }

    data.flatMap {
      case (x, related) =>
        related.map {
          case a: Int => Map(keys(1) -> nodeWithId(a), keys(0) -> nodeWithId(x))
          case null => Map(keys(1) -> null, keys(0) -> nodeWithId(x))
        }
    }
  }

  private def createFakePipeWith(keys: Array[String], data: (Int, List[Any])*): FakePipe = {
    val in = createFakeDataWith(keys, data: _*)

    new FakePipe(in)
  }

  private def createFakeArgumentPipeWith(keys: Array[String], data: (Int, List[Any])*): Pipe = {
    val in = createFakeDataWith(keys, data: _*)

    new Pipe {
      override def internalCreateResults(state: QueryState): Iterator[ExecutionContext] = state.initialContext match {
        case Some(context: ExecutionContext) =>
          in.flatMap { m =>
            if (ValueUtils.of(m(keys(0))) == context.getByName(keys(0))) {
              val stringToProxy: mutable.Map[String, AnyValue] = collection.mutable.Map(m.mapValues(ValueUtils.of).toSeq: _*)
              val outRow = state.newExecutionContext(CommunityExecutionContextFactory())
              outRow.mergeWith(ExecutionContext(stringToProxy), null)
              Some(outRow)
            }
            else None
          }.iterator
        case _ => Iterator.empty
      }

      override def id: Id = Id.INVALID_ID
    }
  }

}
