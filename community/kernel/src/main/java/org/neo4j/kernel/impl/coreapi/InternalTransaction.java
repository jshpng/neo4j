/*
 * Copyright (c) 2002-2019 "Neo4j,"
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
package org.neo4j.kernel.impl.coreapi;

import java.util.Map;
import java.util.Optional;

import org.neo4j.graphdb.Transaction;
import org.neo4j.internal.kernel.api.connectioninfo.ClientConnectionInfo;
import org.neo4j.internal.kernel.api.security.SecurityContext;
import org.neo4j.kernel.api.KernelTransaction;
import org.neo4j.kernel.api.exceptions.Status;

public interface InternalTransaction extends Transaction
{
    void setTransaction( KernelTransaction transaction );

    /**
     * Loop-hole to access underlying kernel transaction. This is intended to allow
     * gradual removal of the InternalTransaction interface.
     */
    KernelTransaction kernelTransaction();

    KernelTransaction.Type transactionType();

    SecurityContext securityContext();

    ClientConnectionInfo clientInfo();

    KernelTransaction.Revertable overrideWith( SecurityContext context );

    Optional<Status> terminationReason();

    void setMetaData( Map<String, Object> txMeta );
}
