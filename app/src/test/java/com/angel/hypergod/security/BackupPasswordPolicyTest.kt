package com.angel.hypergod.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPasswordPolicyTest {
    @Test fun rejectsPredictableLengthOnlyPassword() = assertFalse(BackupPasswordPolicy.isStrong("password1234"))
    @Test fun acceptsMixedPassword() = assertTrue(BackupPasswordPolicy.isStrong("Asteri!Limani#2040"))
    @Test fun acceptsLongPassphrase() = assertTrue(BackupPasswordPolicy.isStrong("τέσσερις λέξεις για ασφαλές backup"))
}
