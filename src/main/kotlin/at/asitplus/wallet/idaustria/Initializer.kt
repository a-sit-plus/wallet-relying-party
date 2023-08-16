package at.asitplus.wallet.idaustria

import at.asitplus.wallet.lib.LibraryInitializer
import at.asitplus.wallet.lib.data.CredentialSubject
import io.github.aakira.napier.Napier
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

class Initializer {

    companion object {
//        val mutex = Mutex()

        /**
         * A reference to this class is enough to trigger the init block
         */
        init {
            initWithVcLib()
        }

        private var registered = false

        /**
         * This has to be called first, before anything first, to load the
         * relevant classes of this library into the base implementations of vclib
         */
        fun initWithVcLib() {
            if (registered) return
            Napier.w {
                "custom init with vc lib: start"
            }
            LibraryInitializer.registerExtensionLibrary(
                LibraryInitializer.ExtensionLibraryInfo(
                    credentialScheme = ConstantIndex.IdAustriaCredential,
                    serializersModule = SerializersModule {
                        polymorphic(CredentialSubject::class) {
                            subclass(IdAustriaCredential::class)
                        }
                    },
                )
            )
            registered = true
            Napier.w {
                "custom init with vc lib: end"
            }
        }
    }

}