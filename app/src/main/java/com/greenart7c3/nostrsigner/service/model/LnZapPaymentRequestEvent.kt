package com.greenart7c3.nostrsigner.service.model

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type


// REQUEST OBJECTS

abstract class Request(var method: String? = null)

// PayInvoice Call
class PayInvoiceParams(var invoice: String? = null)

class PayInvoiceMethod(var params: PayInvoiceParams? = null) : Request("pay_invoice") {

    companion object {
        fun create(bolt11: String): PayInvoiceMethod {
            return PayInvoiceMethod(PayInvoiceParams(bolt11))
        }
    }
}

class RequestDeserializer :
    JsonDeserializer<Request?> {
    @Throws(JsonParseException::class)
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): Request? {
        val jsonObject = json.asJsonObject
        val method = jsonObject.get("method")?.asString

        if (method == "pay_invoice") {
            return context.deserialize<PayInvoiceMethod>(jsonObject, PayInvoiceMethod::class.java)
        }
        return null
    }

    companion object {
    }
}
