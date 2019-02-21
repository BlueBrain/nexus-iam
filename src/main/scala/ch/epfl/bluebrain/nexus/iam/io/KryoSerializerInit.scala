package ch.epfl.bluebrain.nexus.iam.io

import com.esotericsoftware.kryo.Kryo
import com.nimbusds.jose.jwk.JWKSet

class KryoSerializerInit {

  def customize(kryo: Kryo): Unit = {
    kryo.register(classOf[JWKSet], new JWKSetSerializer)
    ()
  }
}
