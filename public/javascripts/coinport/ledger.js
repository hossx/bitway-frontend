var app = angular.module('ledger', ['ui.bootstrap', 'pay.app']);

app.controller('LedgerCtrl', function ($scope, $http, $window) {
    $scope.isCollapsed = true;

    var date = new Date();
    $scope.endDate = date.getFullYear() +"-"+ (date.getMonth()+1) + "-" + date.getDate();
    $scope.startDate = date.getFullYear() +"-"+ date.getMonth() + "-" + date.getDate();
    $scope.invoices = [];

    $scope.page = 1;
    $scope.limit = 15;

    $scope.ledgerQuery = function() {
        $http.get('/api/v1/getbills/'+$scope.currency, {params: {
            begin: $scope.startDate,
            end: $scope.endDate,
            limit: $scope.limit,
            skip: $scope.limit*($scope.page-1)}})
            .success(function(data, status, headers, config) {
                $scope.bills = data.items;
                $scope.count = data.count;
            });
    };
    $scope.ledgerQuery();

    $scope.changeCurrency = function() {
        $window.location.href = '/ledger/' + $scope.currency;
    };

    $scope.doRefund = function(item) {
        $window.location.href = "/refund?invoiceId="+item.invoiceId+"&currency="+$scope.currency;;
    };

    $scope.getBalance = function() {
        $http.get('/api/v1/balance/')
            .success(function(data, status, headers, config) {
                data.balances.forEach(function(entry) {
                if (entry.currency == $scope.currency) {
                    $scope.balance = entry.balance;
                    $scope.freezing = entry.freezing;
                }
                });
            });
    };
    $scope.getBalance();
});
