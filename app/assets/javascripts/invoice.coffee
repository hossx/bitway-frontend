shouldPay = btcShouldPay

formatTimeMillis = (milliSeconds) ->
    date = new Date(milliSeconds)
    (1900+date.getYear()) + "/" + (date.getMonth() + 1) + "/" + date.getDate() + " " + date.getHours() + ":" + date.getMinutes()

formatTime = (t) ->
    if t < 10
      '0' + t
    else '' + t

timeFromSecs = (seconds) ->
    formatTime(Math.floor(((seconds/3600)%1)*60)) + ':' +
    formatTime(Math.round(((seconds/60)%1)*60))

updateQRCode = (simple, url) ->
    qrcodeDiv = $("#qrcode")
    qrcodeDiv.empty()

    qrcodeDiv.qrcode({
        render: 'image',
        size: 720,
        fill: "#000",
        background: "#fff",
        ecLevel: 'L',
        text: url,
        radius: (if simple then 0.5 else 0.3)
    });

if invoiceStatus == "NEW"
    simple = true
    updateQRCode(simple, paymentAddress(shouldPay, simple, false))

    $(".qrcode").click ()->
        simple = !simple
        updateQRCode(simple, paymentAddress(shouldPay, simple, false))

if invoiceStatus == "PAID" or invoiceStatus == "CONFIRMED" or invoiceStatus == "COMPLETE"
    $(".paidTime").html(formatTimeMillis(paidTime))

if invoiceStatus == "EXPIRED" or invoiceStatus == "INVALID"
    $(".expiredTime").html(formatTimeMillis(expiredTime))

ttl = invoiceTTL
status = invoiceStatus
updateInterval = 1000


notifyParent = ()->
    if window.parent and window.parent.postMessage
        window.parent.postMessage({"status": status}, "*")

partialPaid = (updateTime, btcShouldPay) ->
    if shouldPay != btcShouldPay
        shouldPay = btcShouldPay
        $("#price").html(btcShouldPay)
        updateQRCode(simple, paymentAddress(btcShouldPay, simple, false))
        $('#payBtn').attr('href', paymentAddress(btcShouldPay, simple, true))
        notifyParent()

becomeExpired = (updateTime) ->
    status = "EXPIRED"

    $(".expiredTime").html(formatTimeMillis(updateTime))
    $("#container").removeClass("new")
    $("#container").removeClass("paid")
    $("#container").addClass("expired")
    notifyParent()

becomePaid = (updateTime) ->
    status = "PAID"

    $(".paidTime").html(formatTimeMillis(updateTime))
    $("#container").removeClass("new")
    $("#container").removeClass("expired")
    $("#container").addClass("paid")
    notifyParent()

toggleDetails = () -> 
    $(".heading").toggleClass("hide-details")
    $(".details").slideToggle( "slow" )

autoThrink = setTimeout(toggleDetails, 3000)

$(".heading").click -> 
    clearTimeout(autoThrink)
    toggleDetails()

if status == "NEW"
    $(document).ready ()->
        updateUI = ()->
            if status != "NEW"
                clearInterval(job)
            else
                $.ajax "/api/" + apiVersion + "/invoice/" + invoiceId + "?f=json&r=" + Math.random(),
                    type: "GET"
                    dataType: "json"
                    error: (jqXHR, textStatus, errorThrown) ->
                    success: (data, textStatus, jqXHR) ->
                        switch data.status.toUpperCase()
                            when "NEW" then partialPaid(data.updateTime, Math.max(data.btcShouldPay, 0.0001))
                            when "EXPIRED" then becomeExpired(data.updateTime)
                            when "INVALID" then becomeExpired(data.updateTime)
                            when "PAID" then becomePaid(data.updateTime)
                            when "CONFIRMED" then becomePaid(data.updateTime)
                            when "COMPLETE" then becomePaid(data.updateTime)
                percent = Math.min(100, 100*(invoiceLife - ttl)/invoiceLife)
                $(".indi-cover").width(percent + "%")
                $(".deadlineTime").html(timeFromSecs(ttl/1000))

                if ttl < 0
                    clearInterval(job)
                    becomeExpired(invoiceExpirationTime) if status == "NEW"

        job = setInterval( ()->
            ttl = ttl - updateInterval
            updateUI()
        , updateInterval)

        updateUI()

        ua = navigator.userAgent.toLowerCase()
        if /micromessenger/.test(ua)
            $(document.body).addClass("noscroll")
            $("#wechat-cover").show()
