shorty-dqw
==========

An investigation into a URL shortener with Spray and Scala, to be deployed to
[Heroku](http://heroku.com "Heroku").

Description
-----------

This is a simple REST service, using [Scala](http://scala-lang.org "Scala"),
[Spray](http://spray.io "Spray") and [PostgreSQL](http://postgresql.org "PostgreSQL").

The API works as follows:

- `POST` to `/shorty/hashes` in order to create a new 5 character hash to a new
  URL, providing an `application/json` body that corresponds to the following:

      {
        "urlToShorten": "the url that you want to have shortened",
        "encodedPrefix": "whatever you want shoved on the beginning of the hash"
      }

  Note that the `encodedPrefix` is **optional**.  If you don't specify anything
  then you'll get the hash in the response.  If you specify something then
  you'll still get the hash, but the `encodedPrefix` will be the prefix of the
  hash.  For example:

      {
        "urlToShorten": "http://somewhere.com/and/a/path",
        "encodedPrefix": "http://shor.ty/sd8eW"
      }

  The response is in JSON format, containing:

      {
        "shortened": "the resulting hash with or without the encodedPrefix",
        "equatesTo": "the same URL you asked to have shortened",
        "ref": "a URL reference to can use to retrieve metrics about the hash"
      }

  **Return codes**:
  
  - 200 on success
  - 500 on internal error
  - 503 when things are experiencing temporary problems

- `GET` to `/shorty/hashes/<hash>` will return some metrics about the hash
  you've specified.  This URL is available as the `ref` memeber of the response
  from the `POST`.  The response you receive is a JSON payload that looks like:

      {
        "hash": "the hash you specified",
        "url": "the url to which the hash resolves",
        "clickCount": the number of clicks that this hash has received
      }

  **Return codes**:
  
  - 200 on success
  - 404 if the hash can't be resolved
  - 500 on internal error
  - 503 when things are experiencing temporary problems

- `GET` to `/shorty/redirect/<hash>` will return a permanent redirect to the URL
  to which the hash resolves.

  **Return codes**:

  - 308 (permanent redirect) on success
  - 404 if the hash can't be resolved
  - 500 on internal error


Design
------

There were a few goals to this:

1. Deploy on [Heroku](http://heroku.com "Heroku") - I've never done this before
2. Build it reactively [Scala](http://scala-lang.org "Scala"), cuz that's awesome
3. Get a decently scalable solution

Goal 1) was met. I had some interesting trouble getting things up on Heroku, but
that was just because it took me some time to understand how they get things
done, and how to configure the DB properly.  I went from zero to deployed in a
crazy short amount of time when you really consider the complexity of what
Heroku has accomplished.

Goal 2) was met. It's fully non-blocking and asynchronous.
[Spray](http://spray.io "Spray") is really awesome.  There are some quirks to it
that require a bit of effort, but it's incredibly flexible and deterministic in
the face of a lot of asynchronous code.  Hooking up
[Akka](http://akka.io "Akka")'s [Circuit Breaker](http://doc.akka.io/docs/akka/2.2.3/common/circuitbreaker.html "Circuit Breaker")
really felt good.  Simple, effective, just damn awesome.  I also enjoyed testing
out the [PostgreSQL Async Library](https://github.com/mauricio/postgresql-async "PostgreSQL Async Library")
for the first time.  [Maurício](https://github.com/mauricio "Maurício")'s done a
decent job as far as I can tell.  It's good enough for my purposes, but I can't
say I've done a thorough investigation of it.

Goal 3) hasn't really been met all that well.  The logic that creates the hash
has a number of strategies, but the one used in the production code is "random".
This helps for the security aspect of things, but it's also pretty stupid.
Every call has to generate a new random hash, which may have already been
generated, so we have to check the DB.  If there are 10 consecutive clashes,
then we give up.

**A "Sharded" Solution**

The [scalable-solution-wip](https://github.com/derekwyatt/shorty-dqw/tree/scalable-solution-wip "'Scalable' Solution")
branch is an attempt to fix this.  The `ShardedShortyLogicComponent` in the
[ShortyLogic.scala](https://github.com/derekwyatt/shorty-dqw/blob/scalable-solution-wip/src/main/scala/org/derekwyatt/shorty/ShortyLogic.scala "ShortyLogic.scala")
file uses the database to create ranges of numbers that can be used to generate
hashes independently of anything else that's currently happening.

_(It's full of race conditions and bugs :D)_

The idea is pretty simple:

- Every server in a cluster would get a unique identifier
  - It doesn't matter what it is, so long as it's at most 64 characters long
  - You can set the value as a Java property, or using the [Hocon](https://github.com/typesafehub/config "Hocon")
    config approach.
  - It's possible that Heroku has an identifier we can use per machine, but
    there's no intrinsic machine identifier we could use that wouldn't change as
    the cloud moves servers around
- When a server runs out of a range of hash codes, it grabs a new set from the
  database for its identifier (range of 50,000 numbers)
- Every time it generates its 1,000th hash, it updates the database to record
  how far it's gotten through its range
  - This is just a premature optimization that reduces the number of updates to
    the DB
- So, if you want to scale out, spin up a new server with a new identifier and
  it should grab a range that it can use exclusively without interfering with
  any other server.
- Then just load-balance as you see fit.

The production code still runs the non-sharded version, since the sharded
version doesn't work all that well right now.

Conclusions
-----------

I'm not sure I'm "done" with this server yet, but it was fun and yielded some
decent learnings.

- Basic deployment to Heroku is cool.  It's simple and effective, if painfully
  slow.  The fact that it does an `sbt clean` is annoying; I tried to figure out
  how to get it to stop doing that, but failed to find anything.
- I'm not sure how Heroku deploys truly scalable solutions, with load
  balancers, cache servers, DNS jiggery pokery, and so forth, but the initial
  investigation was keen.
- The "slowness of deployment" does kill productivity when you actually need
  to do it (most development is all local) because it's death-by-a-million
  cuts at that point.  I hate hurry-up-and-wait loops.
- PostgreSQL integration was straightforward and easy with Maurício's library.
  For something like this, I might not necessarily choose PostgreSQL out of the
  gate, but it does have a great reputation as a key/value store, and that's
  essentially what I'm using it for.
  - The recording of metrics might not be best here.  I would probably use
    Cassandra for this in production because of the "big table" and temporal
    nature of the click data being recorded.
  - Couple that with the fact that it's high write / low read and Cassandra is
    probably a winner.
- It bugs me that I wasn't able to get the sharded solution working to a level
  of deployability, but I'm just out of time for this sort of work right now.
  - With a bit more effort, it would probably be a pretty decent solution, but
    the devil's in the details.
  - All of the async work provides lots of race conditions and so forth, which
    makes it a decent candiate for Actor work.
  - The simplest thing would be to put it up as a single Actor service, which
    would create a bottleneck that might never be seen.  The time for TCP
    connections to be made, and Spray to do its work may completely dwarf the
    time it takes to generate a hash in sequence.
  - Another approach would have been to use an Agent (or set of Agents) to
    synchronize the effort.
  - Essentially, I want to code this at a higher level than what I would have to
    do now, which is adding some synchronization primitives.
- Anyway... fun stuff. I hope to come back to this at some point to play with it
  a bit more.
