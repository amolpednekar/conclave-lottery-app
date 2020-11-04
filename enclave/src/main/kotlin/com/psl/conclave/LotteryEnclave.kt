package com.psl.conclave

import com.r3.conclave.common.EnclaveCall
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.mail.EnclaveMail
import com.r3.conclave.mail.EnclaveMailId
import java.security.PublicKey
import kotlin.math.floor

class LotteryEnclave : Enclave(), EnclaveCall {

    private val lotteryBook = mutableSetOf<Int>()
    private var result: String? = null
    lateinit var clientPublicKey: PublicKey

    override fun invoke(input: ByteArray): ByteArray? {
        val hostInput = String(input)
        require(hostInput == "DECLARE")
        result = if (lotteryBook.isNotEmpty()) {
            lotteryBook.elementAt(floor(Math.random() * lotteryBook.size).toInt()).toString()
        } else {
            "Lottery empty"
        }
        // Reply to client(s) about the result
        val replyToClient = createMail(clientPublicKey, result!!.toByteArray())
        postMail(replyToClient, null)
        // Tell the host too
        return result!!.toByteArray()
    }

    override fun receiveMail(id: EnclaveMailId, mail: EnclaveMail) {
        clientPublicKey = requireNotNull(mail.authenticatedSender) { "Public Key required" }
        val clientInput = String(mail.bodyAsBytes)
        val lotteryArgs = clientInput.split(":")
        require(lotteryArgs.size == 2) { "Lottery args are incorrect " }

        val response: String
        // Check if it client is registering a vote or asking for the result
        if (lotteryArgs[0] == "BUY") {
            // Require the lottery number to be 6 digit
            require(lotteryArgs[1].matches(Regex("[1234567890]{6}")))

            // Try to add the number to the lottery book
            val number = lotteryArgs[1].toInt()
            if (lotteryBook.contains(number)) {
                response = "Choose a different number"
                // Create and send back the mail with the same topic as the sender used.

            } else {
                response = "Lottery booked!"
                lotteryBook.add(lotteryArgs[1].toInt())
            }
            val reply = createMail(clientPublicKey, response.toByteArray())
            postMail(reply, null)
            return
        }
        if (lotteryArgs[0] == "RESULT") {
            response = result ?: "Results not declared"
            val reply = createMail(clientPublicKey, response.toByteArray())
            postMail(reply, null)
            return
        } else {
            response = "Invalid input"
            val reply = createMail(clientPublicKey, response.toByteArray())
            postMail(reply, null)
            return
        }
    }
}