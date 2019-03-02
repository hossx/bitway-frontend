var app = angular.module('refund', ['pay.app', 'ui.bootstrap']);

app.controller('RefundCtrl', ['$scope', '$http', '$interval', function ($scope, $http, $interval) {
    $scope.refundData = {};
    $scope.balance = 0.0;
    $scope.refundData = {currency: $scope.currency, emailuuid: "empty"};

    $scope.getBalance = function() {
        $http.get('/api/v1/balance/')
            .success(function(data, status, headers, config) {
                data.balances.forEach(function(entry) {
                    if (entry.currency == $scope.currency) $scope.balance = entry.balance;
                });
            });
    };
    $scope.getBalance();

    $scope.page = 1;
    $scope.limit = 10;

    $scope.loadrefunds = function () {
        $http.get('/api/v1/queryrefund', {params: {limit: $scope.limit, skip: $scope.limit*($scope.page-1), 'type': 1}})
            .success(function (data, status, headers, config) {
                $scope.refunds = data.items;
                $scope.count = data.count;
            });
    };
    $scope.loadrefunds();

    $scope.refund = function () {
        $http.post('/api/v1/refund/', {data: $scope.refundData})
            .success(function (data, status, headers, config) {
                if (data.rv) {
                    alert("提交成功");
                } else {
                    alert("提交失败，错误代码为："+data.error);
                }
                setTimeout($scope.getBalance(), 1000);
                setTimeout($scope.loadrefunds(), 1000);
                $scope.getClearData();
            });
    };

    $scope.getClearData = function() {
        $scope.refundData.address = "";
        $scope.refundData.amount = "";
        $scope.refundData.emailcode = "";
        $scope.stopTimingEmail();
    };

    $scope.confirmRefund = function (refundId) {
      $http.post('/api/v1/confirmrefund', {"refundId": refundId, "cancel": false})
          .success(function(data, status, headers, config) {
               alert("确认成功");
               setTimeout($scope.getBalance(), 500);
               setTimeout($scope.loadrefunds(), 500);
        });
    };

    $scope.cancelRefund = function (refundId) {
        $http.post('/api/v1/confirmrefund', {"refundId": refundId, "cancel": true})
            .success(function(data, status, headers, config) {
                alert("取消成功");
                setTimeout($scope.getBalance(), 500);
                setTimeout($scope.loadrefunds(), 500);
            });
    };

    // email verification code and button timer:
    $scope.showrefundError = false;
    $scope.verifyButtonEmail = "获取邮件验证码";
    var _stop;
    $scope.isTimingEmail = false;

    $scope.disableButtonEmail = function () {
        if (angular.isDefined(_stop)) {
            $scope.isTimingEmail = true;
            return;
        }

        $scope.secondsEmail = 120;

        _stop = $interval(function () {
            if ($scope.secondsEmail > 0) {
                $scope.secondsEmail = $scope.secondsEmail - 1;
                $scope.verifyButtonEmail = ' '+ $scope.secondsEmail + '秒后重新获取邮件验证码';
                $scope.isTimingEmail = true;
            }
            else {
                $scope.stopTimingEmail();
                $scope.verifyButtonEmail = "获取邮件验证码";
            }
        }, 1000);
    };

    $scope.stopTimingEmail = function () {
        if (angular.isDefined(_stop)) {
            $interval.cancel(_stop);
            _stop = undefined;
        }
        $scope.isTimingEmail = false;
        $scope.secondsEmail = 0;
        $scope.verifyButtonEmail = "获取邮件验证码";
    };

    $scope.$on('destroy', function () {
        $scope.stopTimingEmail();
    });

    $scope.sendVerifyEmail = function () {
        $scope.showrefundError = false;
        $scope.disableButtonEmail();

        $http.get('/sendverificationemail')
            .success(function (data, status, headers, config) {
                $scope.refundData.emailuuid = data.uuid;
            });
    };
}]);
