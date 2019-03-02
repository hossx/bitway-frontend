// JavaScript Document
function refreshPrice() {
    $.get('/api/v1/rates/cny',function(data, status){
        console.log('get price', data);
        $("#price").html('1 BTC = ' + data['5000.0'] + '元');
    });
    setTimeout(refreshPrice, 5000);
}

$(document).ready(function(){
    refreshPrice();
});