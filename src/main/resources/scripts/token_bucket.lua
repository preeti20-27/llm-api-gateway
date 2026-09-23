-- Token-bucket rate limiter.
--
-- KEYS[1] = bucket key (a Redis hash storing "tokens" and "timestamp")
-- ARGV[1] = capacity          (max tokens the bucket can hold)
-- ARGV[2] = refill_rate       (tokens added per second)
-- ARGV[3] = now                (current time in MILLISECONDS, integer)
-- ARGV[4] = requested          (tokens this call costs; always 1 here)
--
-- Returns { allowed (1/0), remaining_tokens (integer, floored), retry_after_seconds (integer, 0 if allowed) }
--
-- Runs as a single script so Redis executes it atomically: the read (current
-- tokens + last refill time), the refill math, and the write (decrement + save)
-- all happen as one indivisible step from every other client's point of view.
-- Without that, two concurrent requests against the same key could both read
-- "1 token left", both independently decide to allow themselves, and both
-- decrement -- letting two requests through on a budget of one. Ordinary
-- application code (read, then decide, then write as separate Redis calls)
-- cannot avoid this race; Lua scripting is Redis's mechanism for exactly this.
--
-- `now` is passed as integer epoch MILLISECONDS, not fractional seconds, and
-- deliberately: current epoch time (~1.7e12 ms) is still an exact integer in a
-- double, so `now - last_refill` below is exact. Epoch SECONDS as a float
-- (~1.7e9 with a sub-second fraction) is not -- two nearly-equal ~10-digit
-- floats subtracted lose precision in exactly the small digits that hold the
-- elapsed time, which silently corrupted the refill math (caught by
-- RateLimiterTest.refillsTokensAfterWindowElapses).

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local now_ms = tonumber(ARGV[3])
local requested = tonumber(ARGV[4])

local bucket = redis.call('HMGET', key, 'tokens', 'timestamp')
local tokens = tonumber(bucket[1])
local last_refill_ms = tonumber(bucket[2])

if tokens == nil then
    -- first request ever seen for this key: start the bucket full
    tokens = capacity
    last_refill_ms = now_ms
end

local elapsed_seconds = math.max(0, now_ms - last_refill_ms) / 1000
tokens = math.min(capacity, tokens + (elapsed_seconds * refill_rate))

local allowed = 0
local retry_after = 0

if tokens >= requested then
    tokens = tokens - requested
    allowed = 1
else
    local deficit = requested - tokens
    retry_after = math.ceil(deficit / refill_rate)
end

redis.call('HMSET', key, 'tokens', tostring(tokens), 'timestamp', tostring(now_ms))
-- A bucket left untouched refills completely after (capacity / refill_rate)
-- seconds, at which point its stored state carries no information -- let Redis
-- reclaim it instead of keeping one hash per API key forever.
redis.call('EXPIRE', key, math.ceil(capacity / refill_rate) + 1)

return { allowed, math.floor(tokens), retry_after }
