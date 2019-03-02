var filterApp = angular.module('pay.app', []);

filterApp.filter('billTypeText', function() {
    var filter = function(input) {
        if(input == 0)
            return "收入";
        if(input == 1)
            return "费用";
        if(input == 2)
            return "退款";
        if(input == 3)
            return "提现";
        return input;
    };
    return filter;
});

filterApp.filter('billTypeClass', function() {
    var filter = function(input) {
        if(input == 0)
            return "success";
        if(input == 1)
            return "danger";
        if(input == 2)
            return "warning";
        if(input == 3)
            return "info";
        return input;
    };
    return filter;
});

filterApp.filter('cashFlowColor', function() {
    var filter = function(input) {
        if(input == "-")
            return "text-danger";
        if(input == "+")
            return "text-success";
        return input;
    };
    return filter;
});

filterApp.filter('transferStatusText', function() {
    var status = ['等待处理', '处理中', '确认中', '已确认', '成功', '失败', '重组中', '重组成功', '已取消', '被驳回'];
    var filter = function(input) {
        return status[input];
    };
    return filter;
});

filterApp.filter('transferStatusText2', function() {
    var status = {'New':'新创建', 'Paid':'已支付', 'Confirmed':'已确认', 'Complete':'已完成', 'Invalid':'无效', 'Expired':'已过期'};
    var filter = function(input) {
        return status[input];
    };
    return filter;
});

filterApp.filter('cash', function() {
    return function(input, param) {
        if (!input) return input;
        var decimal = 2;
        if (param) decimal = param;
        return parseFloat(input).toFixed(decimal);
    };
});

filterApp.filter('abstract', function() {
    return function(input, param) {
        if (!input) return input;
        var len = 5;
        if (param) len = param;
        var result = input.substr(0, len);
        if(input.length > len) result += '...';
        return result;
    };
});
