int cas(int *ptr, int oldval, int newval);

static void spin_lock(int *lock) {
    while (1) {
        // if lock is 1, meaning acquired by others, cas will return 0, not able to acquire lock
        // if lock is 0, meaning not acquired, cas will return 1, and lock is acquired successfully
        if (cas(lock, 0, 1)) return;

        // keep polling lock status
        while (*lock) cpu_relax();
    }
}

static void spin_unlock(int *lock) {
    // clear the cpu cache with the barrier function so that others waiting for the lock will read the new value for lock
    barrier();
    // release the lock
    *lock = 0;
}