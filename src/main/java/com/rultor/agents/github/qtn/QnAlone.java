/**
 * Copyright (c) 2009-2014, rultor.com
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met: 1) Redistributions of source code must retain the above
 * copyright notice, this list of conditions and the following
 * disclaimer. 2) Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided
 * with the distribution. 3) Neither the name of the rultor.com nor
 * the names of its contributors may be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT
 * NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.rultor.agents.github.qtn;

import co.stateful.Lock;
import co.stateful.Locks;
import com.jcabi.aspects.Immutable;
import com.jcabi.github.Comment;
import com.jcabi.github.Repo;
import com.jcabi.log.Logger;
import com.jcabi.xml.XML;
import com.rultor.agents.github.Question;
import com.rultor.agents.github.Req;
import com.rultor.spi.Talk;
import java.io.IOException;
import java.net.URI;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Passes through only if it is alone in this repo.
 *
 * @author Yegor Bugayenko (yegor@tpc2.com)
 * @version $Id$
 * @since 1.3
 */
@Immutable
@ToString
@EqualsAndHashCode(of = { "talk", "locks", "origin" })
public final class QnAlone implements Question {

    /**
     * Talk.
     */
    private final transient Talk talk;

    /**
     * Locks to use.
     */
    private final transient Locks locks;

    /**
     * Original question.
     */
    private final transient Question origin;

    /**
     * Ctor.
     * @param tlk Talk
     * @param lcks Locks
     * @param qtn Original question
     */
    public QnAlone(final Talk tlk, final Locks lcks, final Question qtn) {
        this.talk = tlk;
        this.locks = lcks;
        this.origin = qtn;
    }

    @Override
    public Req understand(final Comment.Smart comment,
        final URI home) throws IOException {
        final Repo repo = comment.issue().repo();
        final XML xml = this.talk.read();
        final String name = xml.xpath("/talk/@name").get(0);
        final Lock lock = this.lock(repo);
        if (xml.nodes("/talk[request or daemon]").isEmpty()) {
            lock.unlock(name);
            Logger.info(this, "%s unlocked by %s", repo.coordinates(), name);
        }
        final Req req;
        if (lock.lock(name)) {
            Logger.info(this, "%s locked for %s", repo.coordinates(), name);
            req = this.origin.understand(comment, home);
        } else {
            req = Req.LATER;
        }
        if (req.equals(Req.EMPTY)) {
            Logger.info(
                this, "%s unlocked %s since REQ is empty",
                repo.coordinates(), name
            );
            lock.unlock(name);
        }
        return req;
    }

    /**
     * Get lock by repo.
     * @param repo Github repo
     * @return Lock
     * @throws IOException If fails
     */
    private Lock lock(final Repo repo) throws IOException {
        return this.locks.get(
            String.format("rt-alone-%s", repo.coordinates()).replaceAll(
                "[^a-zA-Z0-9\\-]", "-"
            )
        );
    }

}
