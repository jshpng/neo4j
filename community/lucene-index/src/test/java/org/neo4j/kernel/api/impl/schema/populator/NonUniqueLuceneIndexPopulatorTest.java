/*
 * Copyright (c) 2002-2016 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
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
package org.neo4j.kernel.api.impl.schema.populator;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.neo4j.collection.primitive.PrimitiveLongCollections;
import org.neo4j.io.IOUtils;
import org.neo4j.io.fs.DefaultFileSystemAbstraction;
import org.neo4j.kernel.api.impl.index.storage.DirectoryFactory;
import org.neo4j.kernel.api.impl.index.storage.PartitionedIndexStorage;
import org.neo4j.kernel.api.impl.schema.LuceneSchemaIndex;
import org.neo4j.kernel.api.impl.schema.LuceneSchemaIndexBuilder;
import org.neo4j.kernel.api.index.NodePropertyUpdate;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.impl.api.index.sampling.IndexSamplingConfig;
import org.neo4j.register.Register;
import org.neo4j.register.Registers;
import org.neo4j.storageengine.api.schema.IndexReader;
import org.neo4j.test.TargetDirectory;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class NonUniqueLuceneIndexPopulatorTest
{
    @Rule
    public final TargetDirectory.TestDirectory testDir = TargetDirectory.testDirForTest( getClass() );

    private final DirectoryFactory dirFactory = new DirectoryFactory.InMemoryDirectoryFactory();

    private LuceneSchemaIndex index;
    private NonUniqueLuceneIndexPopulator populator;

    @Before
    public void setUp() throws Exception
    {
        DefaultFileSystemAbstraction fs = new DefaultFileSystemAbstraction();
        File folder = testDir.directory( "folder" );
        PartitionedIndexStorage indexStorage = new PartitionedIndexStorage( dirFactory, fs, folder, "testIndex" );

        index = LuceneSchemaIndexBuilder.create()
                .withIndexStorage( indexStorage )
                .build();
    }

    @After
    public void tearDown() throws Exception
    {
        if ( populator != null )
        {
            populator.close( false );
        }
        IOUtils.closeAll( index, dirFactory );
    }

    @Test
    public void sampleEmptyIndex() throws IOException
    {
        populator = newPopulator();

        Register.DoubleLongRegister register = Registers.newDoubleLongRegister();
        long indexSize = populator.sampleResult( register );
        long uniqueElements = register.readFirst();
        long sampleSize = register.readSecond();

        assertEquals( 0, indexSize );
        assertEquals( 0, uniqueElements );
        assertEquals( 0, sampleSize );
    }

    @Test
    public void sampleAddedUpdates() throws Exception
    {
        populator = newPopulator();

        List<NodePropertyUpdate> updates = Arrays.asList(
                NodePropertyUpdate.add( 1, 1, "aaa", new long[]{1} ),
                NodePropertyUpdate.add( 2, 1, "bbb", new long[]{1} ),
                NodePropertyUpdate.add( 3, 1, "ccc", new long[]{1} ) );

        populator.add( updates );

        Register.DoubleLongRegister register = Registers.newDoubleLongRegister();
        long indexSize = populator.sampleResult( register );
        long uniqueElements = register.readFirst();
        long sampleSize = register.readSecond();

        assertEquals( 3, indexSize );
        assertEquals( 3, uniqueElements );
        assertEquals( 3, sampleSize );
    }

    @Test
    public void sampleAddedUpdatesWithDuplicates() throws Exception
    {
        populator = newPopulator();

        List<NodePropertyUpdate> updates = Arrays.asList(
                NodePropertyUpdate.add( 1, 1, "foo", new long[]{1} ),
                NodePropertyUpdate.add( 2, 1, "bar", new long[]{1} ),
                NodePropertyUpdate.add( 3, 1, "foo", new long[]{1} ) );

        populator.add( updates );

        Register.DoubleLongRegister register = Registers.newDoubleLongRegister();
        long indexSize = populator.sampleResult( register );
        long uniqueElements = register.readFirst();
        long sampleSize = register.readSecond();

        assertEquals( 3, indexSize );
        assertEquals( 2, uniqueElements );
        assertEquals( 3, sampleSize );
    }

    @Test
    public void addUpdates() throws Exception
    {
        populator = newPopulator();

        List<NodePropertyUpdate> updates = Arrays.asList(
                NodePropertyUpdate.add( 1, 1, "foo", new long[]{1} ),
                NodePropertyUpdate.add( 2, 1, "bar", new long[]{1} ),
                NodePropertyUpdate.add( 42, 1, "bar", new long[]{1} ) );

        populator.add( updates );

        index.maybeRefreshBlocking();
        try ( IndexReader reader = index.getIndexReader() )
        {
            assertArrayEquals( new long[]{1, 2, 42}, PrimitiveLongCollections.asArray( reader.scan() ) );
        }
    }

    private NonUniqueLuceneIndexPopulator newPopulator() throws IOException
    {
        IndexSamplingConfig samplingConfig = new IndexSamplingConfig( new Config() );
        NonUniqueLuceneIndexPopulator populator = new NonUniqueLuceneIndexPopulator( index, samplingConfig );
        populator.create();
        return populator;
    }
}
