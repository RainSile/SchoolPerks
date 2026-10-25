# 业务测试用例执行索引

日期：2026-10-26。只收录本轮白名单套件的最新结果，不将 target 中历史 AI 测试报告混入统计。先前新测试的前置条件/断言问题和重跑保留在私有分阶段日志，不能当作产品 bug 或隐藏的首次通过。

最新唯一用例：155，通过 155，失败 0，错误 0，跳过 0。该条数不等于业务分支数、接口数或页面覆盖率。

## AnalyticsStageSevenMySqlTest

模块：campus-boot；12 项，失败 0，错误 0，跳过 0。

- PASS：`pausedStoreRetainsHistoricalStatistics`。
- PASS：`computesEventTimeGrossMoneyAndCohortWithoutJoinFanout`。
- PASS：`aopPersistsSuccessDenialAndFailureWithoutSensitivePayloads`。
- PASS：`platformSumsStoresAndLosesAccessImmediatelyAfterRoleRevocation`。
- PASS：`validatesBoundsAndLegacyEntryRequiresExplicitStore`。
- PASS：`queriesOperationalAuditWithFiltersAndRequiresAuditPermission`。
- PASS：`separatesPortalsAndMembersAndDoesNotTrustForgedJwtAuthorities`。
- PASS：`operationalSinkFailureDoesNotTurnSuccessfulRequestIntoError`。
- PASS：`cacheIsScopedAndRefreshesButCannotOutliveMembershipOrSession`。
- PASS：`daylightSavingUsesZoneRulesWithoutMysqlTimeZoneTables`。
- PASS：`respectsInclusiveLocalDatesHalfOpenUtcBoundariesAndZeroDays`。
- PASS：`rbacWriteKeepsAtomicBusinessAuditAndAddsOperationalResult`。

## AuthenticationMySqlTest

模块：campus-boot；11 项，失败 0，错误 0，跳过 0。

- PASS：`resendInvalidatesOldChallengeAndPhoneRateLimitSurvivesDenials`。
- PASS：`onlyOwnProfileCanChangeAndUnknownFieldsAreRejected`。
- PASS：`logoutAndAbsoluteRefreshExpiryInvalidateSessions`。
- PASS：`concurrentCodeConsumptionAndRefreshReplayAreSerialized`。
- PASS：`ipLimitCannotBeBypassedWithOtherPhones`。
- PASS：`loginRegistersOnceConsumesCodeAndNeverLeaksCredentialsInProfile`。
- PASS：`campusApplicationsAreEncryptedScopedAndDoNotAutoApprove`。
- PASS：`missingAndFailingSmsProvidersReturn503WithoutIssuingUsableChallenges`。
- PASS：`expiredWrongAndExhaustedCodesCannotBeUsed`。
- PASS：`permissionsAndBansAreCheckedAgainstTheDatabaseForEveryRequest`。
- PASS：`refreshRotatesAndReplayRevokesTheWholeFamily`。

## BusinessEndpointInventoryTest

模块：campus-boot；2 项，失败 0，错误 0，跳过 0。

- PASS：`inventoriesEveryRegisteredBusinessOperationAndItsDocumentation`。
- PASS：`checksAnonymousDenialForEveryBearerDocumentedBusinessOperation`。

## BusinessLiveJourneyMySqlTest

模块：campus-boot；1 项，失败 0，错误 0，跳过 0。

- PASS：`businessRealBrowserCouponJourney`。

## BusinessLiveMapMySqlTest

模块：campus-boot；1 项，失败 0，错误 0，跳过 0。

- PASS：`businessRealMapSdkAndProxy`。

## BusinessReadChainsMySqlTest

模块：campus-boot；14 项，失败 0，错误 0，跳过 0。

- PASS：`businessEveryBearerOperationRejectsAnonymousAndInvalidTokensWithLiveSecurity`。
- PASS：`businessCommunityQueuesAndOwnQueriesRespectReviewVisibility`。
- PASS：`businessReadyCommunityImagesReturnScopedDownloadTickets`。
- PASS：`businessCapabilitiesLocationAndRebuildRecoveryHaveQueryCoverage`。
- PASS：`businessUnconfiguredMapProxyFailsExplicitlyWithoutProviderCalls`。
- PASS：`businessEveryJsonBodyRejectsMalformedJsonBeforeCallingItsService`。
- PASS：`businessTradeQueriesPreserveSnapshotsAndSeparateBuyerAndStoreViews`。
- PASS：`businessEveryPortalBoundOperationRejectsBothOtherPortals`。
- PASS：`businessRbacQueriesAndAssignmentHaveRealPersistentResults`。
- PASS：`businessPlatformAdministratorUsesAllPortalsWithoutChangingIdentityOrStoreOwner`。
- PASS：`businessGroupProgressDoesNotExposeMemberIdentities`。
- PASS：`businessCouponReadChainIncludesManagementReviewClaimsAndRedemptionHistory`。
- PASS：`businessNotificationAndDeliveryQueriesExposeMetadataWithoutRawPayloads`。
- PASS：`businessMerchantFileAndCampusQueriesRespectOwnership`。

## CommunityStageFiveMySqlTest

模块：campus-boot；19 项，失败 0，错误 0，跳过 0。

- PASS：`localPolicyFiltersNormalizedTermsAndDoesNotPretendAutomaticReview`。
- PASS：`wrongCoordinateSystemInvalidRangeAndStaleLocationAreRejected`。
- PASS：`reportsAreUniqueAndPrivateAndResolutionIsAudited`。
- PASS：`expiredRebuildLeaseRejectsOldWorkerAcknowledgement`。
- PASS：`elasticsearchCandidatesAreHydratedOnlyFromCurrentPublicRows`。
- PASS：`communityImagesMustBeReadyOwnedAndHaveSeparatePurpose`。
- PASS：`draftsArePrivateAndClientCannotAssignAuthorOrStatus`。
- PASS：`twoConcurrentLikesCreateOnlyOneRelation`。
- PASS：`hiddenAndDeletedPostsDisappearBeforeIndexDeliveryAndOldEventsCannotRestoreThem`。
- PASS：`rebuildCreatesProjectionAndIncludesTombstoneKeys`。
- PASS：`pausedMerchantsAndUnauthorisedLocationWritersAreExcluded`。
- PASS：`editingPublishedContentWithdrawsItUntilNewApproval`。
- PASS：`likesAreIdempotentAndAnonymousOrHiddenMutationsFail`。
- PASS：`searchLimitsAndLiteralHighlightNeverAcceptArbitraryFieldsOrHtml`。
- PASS：`commentHidingAndAuthorDeletionCannotBeUndoneByOldModeration`。
- PASS：`immutableReviewAndSelfReviewAreEnforced`。
- PASS：`commentsArePrivateUntilReviewedAndRetryKeysDoNotDuplicateThem`。
- PASS：`nearbyUsesDeclaredWgs84AndStableDistanceOrdering`。
- PASS：`moderationOutboxFailureRollsBackPublicationAndReviewDecision`。

## CouponStageThreeMySqlTest

模块：campus-boot；18 项，失败 0，错误 0，跳过 0。

- PASS：`offlineStopsClaimsButHonorsExistingCouponsAndRedemptionIsIdempotent`。
- PASS：`auditFailureRollsBackClaimInventory`。
- PASS：`discountOffersCanBeBrowsedBeforeStartButCannotBeClaimedUntilActive`。
- PASS：`outboxFailureRollsBackTheRedemptionAndItsAudit`。
- PASS：`expiredCodesAndCouponsAreRejectedWithoutChangingRedemptionState`。
- PASS：`realWebSocketAuthenticatesFirstFrameIsolatesUsersAndClosesRevokedSessions`。
- PASS：`campusOnlyOffersRequireApprovedIdentityForTheStoresSchool`。
- PASS：`concurrentClaimsDoNotOversellAndTheSameUserDoesNotDecrementTwice`。
- PASS：`invalidRedemptionAttemptsRemainCountedAcrossFailedRequests`。
- PASS：`expiredWorkerLeaseIsRecoverableAndOldAcknowledgementCannotCompleteNewLease`。
- PASS：`createAndPublishEnforceMembershipReviewRulesAndWhitelist`。
- PASS：`ticketsArePrivateRotatableRateLimitedAndStoreOnlyDigest`。
- PASS：`repeatedClaimReturnsSameResourceAndOnlyDecrementsOnce`。
- PASS：`publicCatalogFiltersAndHidesPausedExpiredAndOfflineOffers`。
- PASS：`concurrentRedemptionsWriteOneReceiptAuditAndOutboxEvent`。
- PASS：`reviewIsImmutableScopedAndCannotApproveSelfOrBeReusedAfterEditing`。
- PASS：`merchantWriteGuardsUseCurrentStateEvenAfterAnOlderRepeatableReadSnapshot`。
- PASS：`failedNotificationsCanRetryWithoutDuplicateInboxAndNeverUndoRedemption`。

## CurrentActorBusinessPermissionTest

模块：campus-boot；3 项，失败 0，错误 0，跳过 0。

- PASS：`optionalPlatformCheckDoesNotBreakInternalDomainGuardsWithoutPortalMetadata`。
- PASS：`onlyExplicitAdminBusinessGrantEnablesExpansion`。
- PASS：`conflictingPortalIdentityNeverEnablesPlatformExpansion`。

## JwtValidationTest

模块：campus-boot；3 项，失败 0，错误 0，跳过 0。

- PASS：`wrongAudienceAndExpiredTokensAreRejected`。
- PASS：`validSignedTokenReachesProtectedService`。
- PASS：`validIdentityStillNeedsAdminPermission`。

## MerchantStageTwoMySqlTest

模块：campus-boot；14 项，失败 0，错误 0，跳过 0。

- PASS：`disabledApplicantsCannotBeApprovedButCanBeRejectedAndCampusSelfReviewIsForbidden`。
- PASS：`merchantsCannotReadOrMutateOtherStoresAndCashiersCannotManageQualifications`。
- PASS：`publicationGuardAllowsOwnersAndManagersButRejectsCashiers`。
- PASS：`storageUnavailableNeverCreatesFakeTicketsOrMetadata`。
- PASS：`auditQueryRequiresIndependentPermissionAndOmitsPrivateContents`。
- PASS：`updatesUseWhitelistAndVersionAndSuspendedStoresAreHiddenAndCannotPublish`。
- PASS：`concurrentReviewCreatesOnlyOneStoreOwnerAndHistoryRecord`。
- PASS：`applicationRequiresOwnReadyQualificationAndDoesNotExposePendingMerchants`。
- PASS：`auditFailureRollsBackApprovalStoreAndOwnerRelation`。
- PASS：`approvalCreatesOneStoreAndLiveMembershipWithoutPlatformRoleEscalation`。
- PASS：`actualSizeMimeSignatureExpiryLimitsAndUntrustedUrlsAreChecked`。
- PASS：`campusReviewRequiresSeparatePermissionAuditsDecryptionAndUpdatesVerifiedSchool`。
- PASS：`uploadValidatesOwnershipAndSealsOnceWithoutExposingKeys`。
- PASS：`rejectedApplicationsRetainHistoryAndAllowResubmissionButSelfReviewIsForbidden`。

## MySqlReadTest

模块：campus-boot；2 项，失败 0，错误 0，跳过 0。

- PASS：`migrationAndMerchantPaginationWorkOnMysql`。
- PASS：`onlyApprovedMerchantsArePublicAndFixtureIsRolledBack`。

## PasswordAuthenticationMySqlTest

模块：campus-boot；10 项，失败 0，错误 0，跳过 0。

- PASS：`passwordAttemptsAreRateLimitedEvenWhenCredentialsFail`。
- PASS：`invalidAbsentDisabledAndSmsOnlyAccountsReturnSameCredentialError`。
- PASS：`clientCannotChooseARoleAndPasswordsMustMeetBytePolicy`。
- PASS：`codesArePurposeBoundAndResetAttemptsExpireOrLock`。
- PASS：`changePasswordRequiresOldPasswordAndInvalidatesAllSessions`。
- PASS：`registrationHashesPasswordAndOnlyCreatesOrdinaryUsers`。
- PASS：`resettingPasswordRevokesEveryOldAccessAndRefreshTokenAndIsSingleUse`。
- PASS：`localAdapterExposesModeNotCodeAndStoresOnlyHmac`。
- PASS：`allThreeExistingAccountTypesUsePasswordLoginAndServerPortals`。
- PASS：`loginCodeCannotRegisterAndRegisterCodeCannotLogin`。

## RbacIsolationMySqlTest

模块：campus-boot；6 项，失败 0，错误 0，跳过 0。

- PASS：`privateApisRejectOtherPortalsEvenWhenPathsAreManuallyEntered`。
- PASS：`roleAssignmentCannotCrossPortalAndCannotAlterSelf`。
- PASS：`disabledAccountsCanBeReenabledByAnotherAdministrator`。
- PASS：`accountPortalChangesRevokeOldSessionAndNewSessionUsesNewHome`。
- PASS：`roleCrudChecksVersionsAndUnknownPermissions`。
- PASS：`profilesExposeExclusiveServerPortalAndIgnoreForgedJwtPermissions`。

## RedemptionQrTest

模块：campus-boot；1 项，失败 0，错误 0，跳过 0。

- PASS：`emittedSvgIsValidXmlAndDecodesToTheExactCredential`。

## ScaffoldContractTest

模块：campus-boot；7 项，失败 0，错误 0，跳过 0。

- PASS：`allowedOriginsAreRestricted`。
- PASS：`bootsWithoutDatabaseOrExternalServices`。
- PASS：`dashboardsAndOperationalAuditNeverReturnFakeSuccessWithoutDatabase`。
- PASS：`protectedResourcesCannotBeAccessedAnonymously`。
- PASS：`validationAndUnfinishedFeaturesUseRealHttpStatuses`。
- PASS：`swaggerAndKnife4jAreActuallyServed`。
- PASS：`disabledAuthenticationIsExplicitAndNeverPretendsToSendSms`。

## TradeStageFourMySqlTest

模块：campus-boot；21 项，失败 0，错误 0，跳过 0。

- PASS：`absentProviderOrderRemainsReconciliableForLatePayment`。
- PASS：`timeoutCancelsAndRejectsPayment`。
- PASS：`callbackEndpointRejectsDuplicateFieldsAndAcceptsVerifiedReceiptIdempotently`。
- PASS：`codeExpiresAndRefundLeaseCanBeRecoveredWithoutChangingId`。
- PASS：`seckillHasOneActiveOrderPerUserAndNoOversell`。
- PASS：`duplicatePaymentAndCallbackHaveOneBusinessTransition`。
- PASS：`refundRetryPreservesProviderIdempotencyAndOneStockRelease`。
- PASS：`impossibleGroupStockAndUnboundedFutureSaleAreRejected`。
- PASS：`failedOutboxRollsBackCancellationAndFailedLocalRefundCommitCanRecover`。
- PASS：`groupFailureRefundsPaidAndCancelsUnpaidOnlyOnce`。
- PASS：`orderEventsHaveExplicitIdempotentInboxRouting`。
- PASS：`groupRequiresPaidMembersAndSettlesAllMembers`。
- PASS：`serverSnapshotAndOrderIdempotencyAreEnforced`。
- PASS：`cashierCannotPublishAndUnrelatedUserCannotReadOrders`。
- PASS：`emptyExpiredGroupStillClosesWithoutLiveOrders`。
- PASS：`callbackAmountMismatchRollsBackPaymentAndReceipt`。
- PASS：`redemptionIsScopedExpiringAndIdempotent`。
- PASS：`missingSandboxNeverCreatesFakePayment`。
- PASS：`cancellationRestoresExactlyOneReservation`。
- PASS：`latePaymentAfterCancellationAlwaysRefunds`。
- PASS：`finiteConcurrencyCannotOversellAndPaymentCancelRaceCannotFulfillTwice`。

## AlipaySandboxAdapterTest

模块：campus-integration；4 项，失败 0，错误 0，跳过 0。

- PASS：`paymentRuntimeHasOneImplementationOfSharedLibraries`。
- PASS：`validRsa2NotificationIsVerifiedAndTamperingRejected`。
- PASS：`checkoutIsSignedOfficialSandboxUrlWithExactServerPrice`。
- PASS：`productionGatewayAndMissingConfigurationCannotBeEnabled`。

## SmsConfigurationTest

模块：campus-integration；3 项，失败 0，错误 0，跳过 0。

- PASS：`simulationUsesOnlyConfiguredCodeAndNeedsNoNetwork`。
- PASS：`simulationFailsWithoutExplicitCodeOrOutsideLocalProfile`。
- PASS：`absentProviderIsUnavailableByDefault`。

## ElasticsearchSearchAdapterTest

模块：campus-integration；3 项，失败 0，错误 0，跳过 0。

- PASS：`tombstonesUseExternalVersionAndMissingAliasNeverAutoCreatesIndex`。
- PASS：`queryUsesOnlyWhitelistedFieldsAndLiteralJsonWithoutHtml`。
- PASS：`explicitVersionIkAndMappingMustMatchBeforeWriting`。
